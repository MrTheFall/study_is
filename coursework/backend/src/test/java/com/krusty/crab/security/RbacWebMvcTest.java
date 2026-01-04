package com.krusty.crab.security;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.krusty.crab.dto.generated.Client;
import com.krusty.crab.dto.generated.Employee;
import com.krusty.crab.dto.generated.Order;
import com.krusty.crab.mapper.ClientMapper;
import com.krusty.crab.mapper.CourierMapper;
import com.krusty.crab.mapper.EmployeeMapper;
import com.krusty.crab.mapper.InventoryMapper;
import com.krusty.crab.mapper.InventoryTransactionMapper;
import com.krusty.crab.mapper.OrderMapper;
import com.krusty.crab.service.AuthService;
import com.krusty.crab.service.ClientService;
import com.krusty.crab.service.CourierService;
import com.krusty.crab.service.EmployeeService;
import com.krusty.crab.service.InventoryService;
import com.krusty.crab.service.OrderService;
import com.krusty.crab.service.ShiftService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class RbacWebMvcTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private InventoryService inventoryService;

    @MockitoBean
    private InventoryMapper inventoryMapper;

    @MockitoBean
    private InventoryTransactionMapper inventoryTransactionMapper;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private CourierService courierService;

    @MockitoBean
    private ClientService clientService;

    @MockitoBean
    private ClientMapper clientMapper;

    @MockitoBean
    private CourierMapper courierMapper;

    @MockitoBean
    private OrderMapper orderMapper;

    @MockitoBean
    private EmployeeService employeeService;

    @MockitoBean
    private ShiftService shiftService;

    @MockitoBean
    private EmployeeMapper employeeMapper;

    @MockitoBean
    private com.krusty.crab.mapper.ShiftMapper shiftMapper;

    @MockitoBean
    private AuthService authService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void inventory_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/inventory").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void inventory_client_forbidden_returns403() throws Exception {
        mockMvc.perform(get("/inventory").with(authentication(clientAuth(100))).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void inventory_manager_allowed_returns200() throws Exception {
        when(inventoryService.getAllInventoryRecords()).thenReturn(List.of());
        when(inventoryMapper.toDtoList(anyList())).thenReturn(List.of());

        mockMvc.perform(get("/inventory")
                        .with(authentication(managerAuth(10002)))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void kitchenQueue_client_forbidden_returns403() throws Exception {
        mockMvc.perform(get("/kitchen/queue")
                        .with(authentication(clientAuth(100)))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void kitchenQueue_cook_allowed_returns200() throws Exception {
        when(orderService.getKitchenQueue()).thenReturn(List.of());

        mockMvc.perform(get("/kitchen/queue")
                        .with(authentication(cookAuth(10004)))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void couriers_cashier_allowed_returns200() throws Exception {
        when(courierService.getAllCouriers()).thenReturn(List.of());
        when(courierMapper.toDtoList(anyList())).thenReturn(List.of());

        mockMvc.perform(get("/couriers")
                        .with(authentication(cashierAuth(10003)))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void assignCourier_cashier_allowed_returns200() throws Exception {
        com.krusty.crab.entity.Order orderEntity = new com.krusty.crab.entity.Order();
        orderEntity.setId(1);

        Order dto = new Order();
        dto.setId(1);
        dto.setCourierId(1);

        when(orderService.assignCourierToOrder(1, 1)).thenReturn(orderEntity);
        when(orderMapper.toDto(orderEntity)).thenReturn(dto);

        mockMvc.perform(patch("/orders/{id}/courier", 1)
                        .with(authentication(cashierAuth(10003)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {"courierId":1}
                    """)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierId").value(1));
    }

    @Test
    void clientProfile_otherClient_forbidden_returns403() throws Exception {
        mockMvc.perform(get("/clients/101")
                        .with(authentication(clientAuth(100)))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void clientProfile_self_allowed_returns200() throws Exception {
        com.krusty.crab.entity.Client clientEntity = new com.krusty.crab.entity.Client();
        clientEntity.setId(100);
        clientEntity.setEmail("client@example.com");

        Client dto = new Client();
        dto.setId(100);
        dto.setEmail("client@example.com");

        when(clientService.getClientById(100)).thenReturn(clientEntity);
        when(clientMapper.toDto(clientEntity)).thenReturn(dto);

        mockMvc.perform(get("/clients/100")
                        .with(authentication(clientAuth(100)))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void employeeProfile_otherEmployee_forbidden_returns403() throws Exception {
        mockMvc.perform(get("/employees/10004")
                        .with(authentication(cashierAuth(10003)))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void employeeProfile_self_allowed_returns200() throws Exception {
        com.krusty.crab.entity.Employee employeeEntity = new com.krusty.crab.entity.Employee();
        employeeEntity.setId(10003);
        employeeEntity.setLogin("cashier");

        Employee dto = new Employee();
        dto.setId(10003);
        dto.setLogin("cashier");

        when(employeeService.getEmployeeById(10003)).thenReturn(employeeEntity);
        when(employeeMapper.toDto(employeeEntity)).thenReturn(dto);

        mockMvc.perform(get("/employees/10003")
                        .with(authentication(cashierAuth(10003)))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void authMe_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/auth/me").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void authMe_authenticated_returns200() throws Exception {
        mockMvc.perform(get("/auth/me").with(authentication(managerAuth(10002))).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(10002))
                .andExpect(jsonPath("$.userType").value("EMPLOYEE"))
                .andExpect(jsonPath("$.role").value("Manager"));
    }

    @Test
    void loginClient_invalidPassword_returns401() throws Exception {
        when(authService.loginClient("client@example.com", "wrong"))
                .thenThrow(new BadCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/auth/login/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                    {"email":"client@example.com","password":"wrong"}
                    """)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private static Authentication clientAuth(int clientId) {
        UserPrincipal principal = new UserPrincipal(clientId, "client@example.com", "CLIENT", null);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));
    }

    private static Authentication cookAuth(int employeeId) {
        UserPrincipal principal = new UserPrincipal(employeeId, "cook", "EMPLOYEE", "Cook");
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"), new SimpleGrantedAuthority("ROLE_Cook")));
    }

    private static Authentication cashierAuth(int employeeId) {
        UserPrincipal principal = new UserPrincipal(employeeId, "cashier", "EMPLOYEE", "Cashier");
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"), new SimpleGrantedAuthority("ROLE_Cashier")));
    }

    private static Authentication managerAuth(int employeeId) {
        UserPrincipal principal = new UserPrincipal(employeeId, "manager", "EMPLOYEE", "Manager");
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"), new SimpleGrantedAuthority("ROLE_Manager")));
    }
}
