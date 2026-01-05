package com.krusty.crab.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krusty.crab.entity.Order;
import com.krusty.crab.entity.enums.OrderStatus;
import com.krusty.crab.repository.OrderRepository;
import com.krusty.crab.security.UserPrincipal;
import com.krusty.crab.security.UserType;
import com.krusty.crab.service.BankSignatureService;
import com.krusty.crab.util.BankPayloadUtil;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles("it")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
class BusinessFlowsIntegrationTest {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("krusty_it")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        if (!postgres.isRunning()) {
            postgres.start();
        }
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private BankSignatureService bankSignatureService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @BeforeAll
    void initDatabase() throws Exception {
        executeSql(Path.of("..", "db", "schema.sql"));
        executeSql(Path.of("..", "db", "functions.sql"));
        executeSql(Path.of("..", "db", "seed.sql"));
    }

    @AfterAll
    void shutdownContainer() {
        postgres.stop();
    }

    @Test
    void takeoutOnlineOrder_requiresPaymentBeforeCashierConfirm_and_generatesKitchenTiming() throws Exception {
        int orderId = placeOrder(
                """
            {
              "clientId": 100,
              "type": "takeout",
              "paymentMethod": "online",
              "items": [
                { "menuItemId": 1, "quantity": 1 }
              ]
            }
            """,
                clientAuth(100));

        mockMvc.perform(patch("/orders/{id}", orderId)
                        .with(authentication(cashierAuth(10003)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    { "status": "confirmed" }
                    """)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Online orders can only be confirmed after successful payment"));

        String startResponse = mockMvc.perform(post("/payments/online/start")
                        .with(authentication(clientAuth(100)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                    {
                      "orderId": %d,
                      "cardNumber": "4111111111111111",
                      "cardExpiry": "12/25",
                      "cardCvv": "123"
                    }
                    """
                                        .formatted(orderId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.redirectUrl").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode startJson = objectMapper.readTree(startResponse);
        String redirectUrl = startJson.get("redirectUrl").asText();
        String transactionId = redirectUrl.substring(redirectUrl.lastIndexOf('/') + 1);
        BigDecimal amount = new BigDecimal(startJson.get("amount").asText());
        String amountStr = amount.stripTrailingZeros().toPlainString();

        long timestamp = Instant.now().getEpochSecond();
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String statusValue = "approved";

        String signature = bankSignatureService.sign(BankPayloadUtil.buildSignaturePayload(
                transactionId, String.valueOf(orderId), amountStr, statusValue, String.valueOf(timestamp), nonce));

        mockMvc.perform(post("/payments/online/return")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("transactionId", transactionId)
                        .param("orderId", String.valueOf(orderId))
                        .param("amount", amountStr)
                        .param("status", statusValue)
                        .param("timestamp", String.valueOf(timestamp))
                        .param("nonce", nonce)
                        .param("signature", signature))
                .andExpect(status().isOk());

        Order confirmed = orderRepository.findById(orderId).orElseThrow();
        assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        mockMvc.perform(patch("/orders/{id}", orderId)
                        .with(authentication(cookAuth(10004)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    { "status": "preparing" }
                    """)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("preparing"));

        mockMvc.perform(patch("/orders/{id}", orderId)
                        .with(authentication(cookAuth(10004)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    { "status": "ready" }
                    """)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"));

        Order ready = orderRepository.findById(orderId).orElseThrow();
        assertThat(ready.getPreparingAt()).isNotNull();
        assertThat(ready.getReadyAt()).isNotNull();
        assertThat(ready.getCookingDurationSeconds()).isNotNull();
        assertThat(ready.getCookingDurationSeconds()).isGreaterThanOrEqualTo(0);

        mockMvc.perform(patch("/orders/{id}", orderId)
                        .with(authentication(cashierAuth(10003)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    { "status": "completed" }
                    """)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"));

        Integer historyCount = jdbcTemplate.queryForObject(
                "select count(*) from order_status_history where order_id = ?", Integer.class, orderId);
        assertThat(historyCount).isEqualTo(4);
    }

    @Test
    void deliveryPayOnReceipt_requiresCourier_and_paymentBeforeCompletion() throws Exception {
        int orderId = placeOrder(
                """
            {
              "clientId": 100,
              "type": "delivery",
              "paymentMethod": "card",
              "deliveryAddress": "Bikini Bottom, Integration Test st. 42",
              "items": [
                { "menuItemId": 1, "quantity": 1 }
              ]
            }
            """,
                clientAuth(100));

        mockMvc.perform(patch("/orders/{id}", orderId)
                        .with(authentication(cashierAuth(10003)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    { "status": "confirmed" }
                    """)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("confirmed"))
                .andExpect(jsonPath("$.acceptedByEmployeeId").value(10003));

        mockMvc.perform(patch("/orders/{id}", orderId)
                        .with(authentication(cookAuth(10004)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    { "status": "preparing" }
                    """)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("preparing"));

        mockMvc.perform(patch("/orders/{id}", orderId)
                        .with(authentication(cookAuth(10004)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    { "status": "ready" }
                    """)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"));

        mockMvc.perform(patch("/orders/{id}/courier", orderId)
                        .with(authentication(managerAuth(10002)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    { "courierId": 1 }
                    """)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierId").value(1));

        mockMvc.perform(patch("/orders/{id}", orderId)
                        .with(authentication(cashierAuth(10003)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    { "status": "delivering" }
                    """)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("delivering"));

        mockMvc.perform(patch("/orders/{id}", orderId)
                        .with(authentication(cashierAuth(10003)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    { "status": "delivered" }
                    """)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("delivered"))
                .andExpect(jsonPath("$.deliveredAt").isNotEmpty());

        mockMvc.perform(patch("/orders/{id}", orderId)
                        .with(authentication(cashierAuth(10003)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    { "status": "completed" }
                    """)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("Delivery orders with pay on receipt must be paid before completion"));

        mockMvc.perform(post("/payments")
                        .with(authentication(cashierAuth(10003)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    { "orderId": %d, "method": "card" }
                    """
                                .formatted(orderId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.method").value("card"))
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(patch("/orders/{id}", orderId)
                        .with(authentication(cashierAuth(10003)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    { "status": "completed" }
                    """)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"));

        Integer historyCount = jdbcTemplate.queryForObject(
                "select count(*) from order_status_history where order_id = ?", Integer.class, orderId);
        assertThat(historyCount).isEqualTo(6);
    }

    @Test
    void inventoryUpdate_createsTransaction_and_analyticsLogsReportView() throws Exception {
        BigDecimal before = jdbcTemplate.queryForObject(
                "select quantity from inventory_records where ingredient_id = ?", BigDecimal.class, 1);

        mockMvc.perform(patch("/inventory/{id}", 1)
                        .with(authentication(managerAuth(10002)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                    { "delta": 10, "reason": "Integration test purchase" }
                    """)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredientId").value(1));

        BigDecimal after = jdbcTemplate.queryForObject(
                "select quantity from inventory_records where ingredient_id = ?", BigDecimal.class, 1);
        assertThat(after).isEqualByComparingTo(before.add(new BigDecimal("10.000")));

        Map<String, Object> tx = jdbcTemplate.queryForMap(
                """
            select source, employee_id, reason, delta
              from inventory_transactions
             where ingredient_id = ?
             order by id desc
             limit 1
            """,
                1);
        assertThat(tx.get("source")).isEqualTo("manual");
        assertThat(((Number) tx.get("employee_id")).intValue()).isEqualTo(10002);
        assertThat(tx.get("reason")).isEqualTo("Integration test purchase");
        assertThat((BigDecimal) tx.get("delta")).isEqualByComparingTo(new BigDecimal("10.000"));

        Integer viewsBefore = jdbcTemplate.queryForObject("select count(*) from report_views", Integer.class);

        mockMvc.perform(get("/analytics/sales")
                        .with(authentication(managerAuth(10002)))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasData").value(true));

        Integer viewsAfter = jdbcTemplate.queryForObject("select count(*) from report_views", Integer.class);
        assertThat(viewsAfter).isEqualTo(viewsBefore + 1);

        Map<String, Object> view = jdbcTemplate.queryForMap(
                """
            select report, employee_id
              from report_views
             order by id desc
             limit 1
            """);
        assertThat(view.get("report")).isEqualTo("sales_summary");
        assertThat(((Number) view.get("employee_id")).intValue()).isEqualTo(10002);
    }

    private int placeOrder(String json, Authentication auth) throws Exception {
        String responseJson = mockMvc.perform(post("/orders")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode node = objectMapper.readTree(responseJson);
        return node.get("orderId").asInt();
    }

    private void executeSql(Path scriptPath) throws Exception {
        String sql = Files.readString(scriptPath, StandardCharsets.UTF_8);
        jdbcTemplate.execute(sql);
    }

    private static Authentication clientAuth(int clientId) {
        UserPrincipal principal = new UserPrincipal(clientId, "client@example.com", UserType.CLIENT, null);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));
    }

    private static Authentication cookAuth(int employeeId) {
        UserPrincipal principal = new UserPrincipal(employeeId, "cook", UserType.EMPLOYEE, "Cook");
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"), new SimpleGrantedAuthority("ROLE_Cook")));
    }

    private static Authentication cashierAuth(int employeeId) {
        UserPrincipal principal = new UserPrincipal(employeeId, "cashier", UserType.EMPLOYEE, "Cashier");
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"), new SimpleGrantedAuthority("ROLE_Cashier")));
    }

    private static Authentication managerAuth(int employeeId) {
        UserPrincipal principal = new UserPrincipal(employeeId, "manager", UserType.EMPLOYEE, "Manager");
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"), new SimpleGrantedAuthority("ROLE_Manager")));
    }
}
