package com.krusty.crab.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.krusty.crab.dto.generated.AuditLogEntry;
import com.krusty.crab.dto.generated.ClientRegistrationRequest;
import com.krusty.crab.dto.generated.ClientUpdateRequest;
import com.krusty.crab.dto.generated.CourierCreateRequest;
import com.krusty.crab.dto.generated.EmployeeCreateRequest;
import com.krusty.crab.dto.generated.MenuItemCreateRequest;
import com.krusty.crab.dto.generated.ReviewCreateRequest;
import com.krusty.crab.dto.generated.ShiftCreateRequest;
import com.krusty.crab.entity.Client;
import com.krusty.crab.entity.Courier;
import com.krusty.crab.entity.Employee;
import com.krusty.crab.entity.EmployeeActionLog;
import com.krusty.crab.entity.EmployeeShift;
import com.krusty.crab.entity.InventoryRecord;
import com.krusty.crab.entity.InventoryTransaction;
import com.krusty.crab.entity.MenuItem;
import com.krusty.crab.entity.Order;
import com.krusty.crab.entity.OrderItem;
import com.krusty.crab.entity.Payment;
import com.krusty.crab.entity.ReportView;
import com.krusty.crab.entity.Review;
import com.krusty.crab.entity.SalaryPayment;
import com.krusty.crab.entity.Shift;
import com.krusty.crab.entity.enums.OrderStatus;
import com.krusty.crab.entity.enums.OrderType;
import com.krusty.crab.entity.enums.PaymentMethod;
import com.krusty.crab.entity.enums.Rating;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class MapperCoverageTest {

    private final ClientMapper clientMapper = Mappers.getMapper(ClientMapper.class);
    private final CourierMapper courierMapper = Mappers.getMapper(CourierMapper.class);
    private final EmployeeMapper employeeMapper = Mappers.getMapper(EmployeeMapper.class);
    private final MenuMapper menuMapper = Mappers.getMapper(MenuMapper.class);
    private final OrderItemMapper orderItemMapper = Mappers.getMapper(OrderItemMapper.class);
    private final PaymentMapper paymentMapper = Mappers.getMapper(PaymentMapper.class);
    private final InventoryMapper inventoryMapper = Mappers.getMapper(InventoryMapper.class);
    private final InventoryTransactionMapper inventoryTransactionMapper =
            Mappers.getMapper(InventoryTransactionMapper.class);
    private final ReviewMapper reviewMapper = Mappers.getMapper(ReviewMapper.class);
    private final ShiftMapper shiftMapper = Mappers.getMapper(ShiftMapper.class);
    private final ReportViewMapper reportViewMapper = Mappers.getMapper(ReportViewMapper.class);
    private final SalaryPaymentMapper salaryPaymentMapper = Mappers.getMapper(SalaryPaymentMapper.class);
    private final EmployeeActionLogMapper employeeActionLogMapper = Mappers.getMapper(EmployeeActionLogMapper.class);

    @Test
    void clientMapper_mapsEntityAndUpdate() {
        ClientRegistrationRequest request = new ClientRegistrationRequest();
        request.setName("Client Name");
        request.setPhone("+123");
        request.setEmail("client@example.com");
        request.setPassword("secret");
        request.setDefaultAddress("Street 1");

        Client entity = clientMapper.toEntity(request);
        assertThat(entity.getId()).isNull();
        assertThat(entity.getEmail()).isEqualTo("client@example.com");
        assertThat(entity.getPasswordHash()).isNull();

        ClientUpdateRequest update = new ClientUpdateRequest();
        update.setName("Updated");
        update.setPhone("+999");
        update.setDefaultAddress("Street 9");

        entity.setEmail("keep@example.com");
        clientMapper.updateEntityFromRequest(update, entity);
        assertThat(entity.getName()).isEqualTo("Updated");
        assertThat(entity.getEmail()).isEqualTo("keep@example.com");

        assertThat(clientMapper.toDtoList(List.of(entity))).hasSize(1);
    }

    @Test
    void courierMapper_mapsEntityAndUpdate() {
        CourierCreateRequest request = new CourierCreateRequest();
        request.setName("Courier");
        request.setPhone("555");
        request.setVehicleInfo("Bike");
        request.setAvailable(true);

        Courier entity = courierMapper.toEntity(request);
        assertThat(entity.getId()).isNull();
        assertThat(entity.getAvailable()).isTrue();

        entity.setBusy(true);
        courierMapper.updateEntityFromRequest(request, entity);
        assertThat(entity.getBusy()).isTrue();

        assertThat(courierMapper.toDtoList(List.of(entity))).hasSize(1);
    }

    @Test
    void employeeMapper_handlesPasswordAndRole() {
        EmployeeCreateRequest request = new EmployeeCreateRequest();
        request.setFullName("Emp");
        request.setLogin("emp");
        request.setPassword("pass");
        request.setRoleId(1);
        request.setContactPhone("123");

        Employee employee = employeeMapper.toEntityWithPassword(request, "pass");
        assertThat(employee.getPasswordHash()).isNotBlank();

        Employee existing = new Employee();
        existing.setPasswordHash("old");
        employeeMapper.updateEntityWithPassword(request, existing);
        assertThat(existing.getPasswordHash()).isNotBlank();

        com.krusty.crab.entity.Role role = new com.krusty.crab.entity.Role();
        role.setId(9);
        existing.setRole(role);
        existing.setHiredAt(LocalDateTime.of(2024, 1, 1, 10, 0));
        assertThat(employeeMapper.toDto(existing).getRoleId()).isEqualTo(9);
    }

    @Test
    void menuMapper_mapsEntityAndUpdate() {
        MenuItemCreateRequest request = new MenuItemCreateRequest();
        request.setName("Burger");
        request.setDescription("Test");
        request.setPrice(BigDecimal.valueOf(9.99));
        request.setAvailable(true);
        request.setPrepTimeMinutes(10);

        MenuItem entity = menuMapper.toEntity(request);
        assertThat(entity.getId()).isNull();

        menuMapper.updateEntityFromRequest(request, entity);
        assertThat(entity.getName()).isEqualTo("Burger");

        assertThat(menuMapper.toDtoList(List.of(entity))).hasSize(1);
    }

    @Test
    void orderItemMapper_mapsOrderAndMenuItem() {
        Order order = new Order();
        order.setId(10);
        MenuItem menuItem = new MenuItem();
        menuItem.setId(20);
        menuItem.setName("Fries");

        OrderItem item = new OrderItem();
        item.setId(1);
        item.setOrder(order);
        item.setMenuItem(menuItem);
        item.setQuantity(2);
        item.setUnitPrice(BigDecimal.valueOf(3));

        com.krusty.crab.dto.generated.OrderItem dto = orderItemMapper.toDto(item);
        assertThat(dto.getOrderId()).isEqualTo(10);
        assertThat(dto.getMenuItemId()).isEqualTo(20);
        assertThat(dto.getName()).isEqualTo("Fries");

        assertThat(orderItemMapper.toDtoList(List.of(item))).hasSize(1);
    }

    @Test
    void orderMapper_mapsOrderFields() throws Exception {
        Order order = new Order();
        order.setId(100);
        order.setType(OrderType.DELIVERY);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentMethod(PaymentMethod.CASH);
        order.setPreparingAt(LocalDateTime.of(2024, 2, 1, 12, 0));
        order.setReadyAt(LocalDateTime.of(2024, 2, 1, 12, 30));
        order.setCreatedAt(LocalDateTime.of(2024, 2, 1, 11, 0));
        order.setUpdatedAt(LocalDateTime.of(2024, 2, 1, 11, 15));
        order.setTotalAmount(BigDecimal.valueOf(20));
        order.setDeliveryAddress("Address");

        Client client = new Client();
        client.setId(1);
        order.setClient(client);

        Employee creator = new Employee();
        creator.setId(2);
        order.setCreatedByEmployee(creator);

        Employee accepter = new Employee();
        accepter.setId(3);
        order.setAcceptedByEmployee(accepter);

        Courier courier = new Courier();
        courier.setId(4);
        courier.setName("Courier");
        order.setCourier(courier);

        OrderMapperImpl mapper = new OrderMapperImpl();
        Field courierField = OrderMapperImpl.class.getDeclaredField("courierMapper");
        courierField.setAccessible(true);
        courierField.set(mapper, courierMapper);

        com.krusty.crab.dto.generated.Order dto = mapper.toDto(order);
        assertThat(dto.getClientId()).isEqualTo(1);
        assertThat(dto.getCourierId()).isEqualTo(4);
        assertThat(dto.getPaymentMethod().getValue()).isEqualTo("cash");
        assertThat(dto.getCreatedAt()).isEqualTo(order.getCreatedAt().atOffset(ZoneOffset.UTC));

        assertThat(mapper.toDtoList(List.of(order))).hasSize(1);
        assertThat(mapper.toPlaceOrderResponse(123).getOrderId()).isEqualTo(123);
    }

    @Test
    void paymentMapper_mapsPaymentAndChange() {
        Order order = new Order();
        order.setId(77);

        Payment payment = new Payment();
        payment.setId(5);
        payment.setOrder(order);
        payment.setMethod(PaymentMethod.CARD);
        payment.setAmount(BigDecimal.valueOf(15.5));
        payment.setPaidAt(LocalDateTime.of(2024, 3, 1, 9, 0));

        com.krusty.crab.dto.generated.Payment dto = paymentMapper.toDto(payment);
        assertThat(dto.getOrderId()).isEqualTo(77);
        assertThat(dto.getMethod().getValue()).isEqualTo("card");
        assertThat(dto.getPaidAt()).isEqualTo(payment.getPaidAt().atOffset(ZoneOffset.UTC));

        com.krusty.crab.dto.generated.ChangeResponse change =
                paymentMapper.toChangeResponse(BigDecimal.valueOf(10), BigDecimal.valueOf(15));
        assertThat(change.getChange()).isEqualByComparingTo(BigDecimal.valueOf(5));
    }

    @Test
    void inventoryMapper_mapsIngredientAndDate() {
        com.krusty.crab.entity.Ingredient ingredient = new com.krusty.crab.entity.Ingredient();
        ingredient.setId(3);

        InventoryRecord record = new InventoryRecord();
        record.setId(2);
        record.setIngredient(ingredient);
        record.setQuantity(BigDecimal.valueOf(4));
        record.setLastUpdated(LocalDateTime.of(2024, 4, 1, 8, 30));

        com.krusty.crab.dto.generated.InventoryRecord dto = inventoryMapper.toDto(record);
        assertThat(dto.getIngredientId()).isEqualTo(3);
        assertThat(dto.getLastUpdated()).isEqualTo(record.getLastUpdated().atOffset(ZoneOffset.UTC));

        assertThat(inventoryMapper.toDtoList(List.of(record))).hasSize(1);
    }

    @Test
    void inventoryTransactionMapper_mapsNestedFields() {
        com.krusty.crab.entity.Ingredient ingredient = new com.krusty.crab.entity.Ingredient();
        ingredient.setId(9);
        ingredient.setName("Salt");

        Employee employee = new Employee();
        employee.setId(12);
        employee.setFullName("Emp Name");

        Order order = new Order();
        order.setId(33);

        InventoryTransaction tx = new InventoryTransaction();
        tx.setId(1);
        tx.setIngredient(ingredient);
        tx.setEmployee(employee);
        tx.setOrder(order);
        tx.setCreatedAt(LocalDateTime.of(2024, 5, 1, 10, 0));
        tx.setDelta(BigDecimal.valueOf(-1));

        com.krusty.crab.dto.generated.InventoryTransaction dto = inventoryTransactionMapper.toDto(tx);
        assertThat(dto.getIngredientId()).isEqualTo(9);
        assertThat(dto.getEmployeeId()).isEqualTo(12);
        assertThat(dto.getOrderId()).isEqualTo(33);
    }

    @Test
    void reviewMapper_mapsRatingAndRelations() {
        ReviewCreateRequest request = new ReviewCreateRequest();
        request.setOrderId(1);
        request.setClientId(2);
        request.setRating(5);
        request.setComment("Great");

        Review review = reviewMapper.toEntity(request);
        assertThat(review.getRating()).isEqualTo(Rating.fromValue(5));

        Order order = new Order();
        order.setId(1);
        Client client = new Client();
        client.setId(2);
        Review full = reviewMapper.toEntityWithRelations(request, order, client);
        assertThat(full.getOrder()).isEqualTo(order);
        assertThat(full.getClient()).isEqualTo(client);

        full.setCreatedAt(LocalDateTime.of(2024, 6, 1, 9, 0));
        com.krusty.crab.dto.generated.Review dto = reviewMapper.toDto(full);
        assertThat(dto.getOrderId()).isEqualTo(1);
        assertThat(dto.getRating()).isEqualTo(5);
    }

    @Test
    void shiftMapper_mapsShiftAndEmployeeShift() {
        ShiftCreateRequest request = new ShiftCreateRequest();
        request.setShiftDate(LocalDate.of(2024, 6, 1));
        request.setStartTime("08:00");
        request.setEndTime("16:00");

        Shift shift = shiftMapper.toEntity(request);
        shift.setId(10);
        assertThat(shiftMapper.toDto(shift).getId()).isEqualTo(10);

        Employee employee = new Employee();
        employee.setId(7);
        EmployeeShift employeeShift = new EmployeeShift();
        employeeShift.setEmployee(employee);
        employeeShift.setShift(shift);

        com.krusty.crab.dto.generated.EmployeeShift dto = shiftMapper.toDto(employeeShift);
        assertThat(dto.getEmployeeId()).isEqualTo(7);
        assertThat(dto.getShiftId()).isEqualTo(10);

        ShiftCreateRequest update = new ShiftCreateRequest();
        update.setShiftDate(LocalDate.of(2024, 6, 2));
        update.setStartTime("09:00");
        update.setEndTime("17:00");
        update.setNote("Note");
        shiftMapper.updateEntityFromRequest(update, shift);
        assertThat(shift.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(shift.getNote()).isEqualTo("Note");

        assertThat(shiftMapper.toDtoList(List.of(shift))).hasSize(1);
        assertThat(shiftMapper.toEmployeeShiftDtoList(List.of(employeeShift))).hasSize(1);
    }

    @Test
    void reportViewMapper_mapsFields() {
        Employee employee = new Employee();
        employee.setId(5);
        employee.setFullName("Reporter");

        ReportView view = new ReportView();
        view.setEmployee(employee);
        view.setReport("sales");
        view.setFromTs(LocalDateTime.of(2024, 7, 1, 0, 0));
        view.setToTs(LocalDateTime.of(2024, 7, 1, 23, 59));
        view.setViewedAt(LocalDateTime.of(2024, 7, 1, 12, 0));

        com.krusty.crab.dto.generated.ReportView dto = reportViewMapper.toDto(view);
        assertThat(dto.getEmployeeId()).isEqualTo(5);
        assertThat(dto.getReport()).isEqualTo("sales");
    }

    @Test
    void salaryPaymentMapper_mapsFields() {
        Employee employee = new Employee();
        employee.setId(6);
        employee.setFullName("Paid Emp");

        SalaryPayment payment = new SalaryPayment();
        payment.setEmployee(employee);
        payment.setPaidAt(LocalDateTime.of(2024, 8, 1, 12, 0));
        payment.setAmount(BigDecimal.valueOf(100));

        com.krusty.crab.dto.generated.SalaryPayment dto = salaryPaymentMapper.toDto(payment);
        assertThat(dto.getEmployeeId()).isEqualTo(6);
        assertThat(dto.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    void employeeActionLogMapper_mapsFields() {
        Employee employee = new Employee();
        employee.setId(11);
        employee.setFullName("Logger");

        EmployeeActionLog log = new EmployeeActionLog();
        log.setEmployee(employee);
        log.setAction("update");
        log.setCreatedAt(LocalDateTime.of(2024, 9, 1, 9, 0));

        AuditLogEntry dto = employeeActionLogMapper.toDto(log);
        assertThat(dto.getEmployeeId()).isEqualTo(11);
        assertThat(dto.getAction()).isEqualTo("update");
    }
}
