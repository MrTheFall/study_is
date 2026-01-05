package com.krusty.crab.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krusty.crab.dto.generated.KitchenQueueItem;
import com.krusty.crab.dto.generated.OrderItemInfo;
import com.krusty.crab.dto.generated.PlaceOrderRequest;
import com.krusty.crab.entity.Courier;
import com.krusty.crab.entity.Employee;
import com.krusty.crab.entity.Order;
import com.krusty.crab.entity.OrderItem;
import com.krusty.crab.entity.enums.OrderStatus;
import com.krusty.crab.entity.enums.PaymentMethod;
import com.krusty.crab.exception.EntityNotFoundException;
import com.krusty.crab.exception.OrderException;
import com.krusty.crab.repository.CourierRepository;
import com.krusty.crab.repository.EmployeeRepository;
import com.krusty.crab.repository.OrderItemRepository;
import com.krusty.crab.repository.OrderRepository;
import com.krusty.crab.util.DbErrorUtil;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CourierRepository courierRepository;
    private final EmployeeRepository employeeRepository;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    private static final List<OrderStatus> COURIER_BUSY_STATUSES = List.of(
            OrderStatus.PENDING,
            OrderStatus.CONFIRMED,
            OrderStatus.PREPARING,
            OrderStatus.READY,
            OrderStatus.DELIVERING);

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            CourierRepository courierRepository,
            EmployeeRepository employeeRepository,
            ObjectMapper objectMapper,
            EntityManager entityManager) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.courierRepository = courierRepository;
        this.employeeRepository = employeeRepository;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
    }

    @Transactional
    public Integer placeOrder(PlaceOrderRequest request, Integer createdByEmployeeId) {
        try {
            List<Map<String, Object>> itemsJson = new ArrayList<>();
            for (com.krusty.crab.dto.generated.OrderItemRequest item : request.getItems()) {
                Map<String, Object> itemMap = new java.util.HashMap<>();
                itemMap.put("menu_item_id", item.getMenuItemId());
                itemMap.put("quantity", item.getQuantity());
                if (item.getUnitPrice() != null) {
                    itemMap.put("unit_price", item.getUnitPrice());
                }
                itemsJson.add(itemMap);
            }

            String itemsJsonb = objectMapper.writeValueAsString(itemsJson);

            String paymentMethod = request.getPaymentMethod() != null
                    ? request.getPaymentMethod().getValue()
                    : null;

            Integer orderId = orderRepository.callPlaceOrder(
                    request.getClientId(),
                    request.getType() != null ? request.getType().getValue() : null,
                    request.getDeliveryAddress(),
                    paymentMethod,
                    itemsJsonb);

            if (createdByEmployeeId != null) {
                Employee employee = employeeRepository
                        .findById(createdByEmployeeId)
                        .orElseThrow(() -> new EntityNotFoundException("Employee", createdByEmployeeId));
                Order order = getOrderById(orderId);
                if (order.getCreatedByEmployee() == null) {
                    order.setCreatedByEmployee(employee);
                    orderRepository.save(order);
                }
            }

            log.info("Order placed successfully with ID: {}", orderId);
            return orderId;
        } catch (JsonProcessingException e) {
            log.error("Error converting items to JSON", e);
            throw new OrderException("Failed to convert order items to JSON", e);
        } catch (DataAccessException e) {
            String dbMessage = DbErrorUtil.extractMeaningfulMessage(e);
            log.error("Database error placing order", e);
            throw new OrderException(dbMessage != null ? dbMessage : "Failed to place order: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error placing order", e);
            throw new OrderException("Failed to place order: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void updateOrderStatus(Integer orderId, OrderStatus newStatus, Integer acceptedByEmployeeId) {
        try {
            orderRepository.callUpdateOrderStatus(orderId, newStatus.getValue());
            entityManager.clear();
            if (newStatus == OrderStatus.CONFIRMED && acceptedByEmployeeId != null) {
                Employee employee = employeeRepository
                        .findById(acceptedByEmployeeId)
                        .orElseThrow(() -> new EntityNotFoundException("Employee", acceptedByEmployeeId));
                Order order = getOrderById(orderId);
                if (order.getAcceptedByEmployee() == null) {
                    order.setAcceptedByEmployee(employee);
                    orderRepository.save(order);
                }
            }
            log.info("Order {} status updated to {}", orderId, newStatus);
        } catch (DataAccessException e) {
            String errorMessage = DbErrorUtil.extractMeaningfulMessage(e);
            if (errorMessage != null) {
                if (errorMessage.contains("insufficient ingredients")) {
                    throw new OrderException(errorMessage);
                }
                if (errorMessage.contains("transition") && errorMessage.contains("is not allowed")) {
                    String message = "Status transition is not allowed";
                    if (errorMessage.contains("->")) {
                        message = "Cannot change order status: "
                                + errorMessage
                                        .substring(
                                                errorMessage.indexOf("transition") + "transition ".length(),
                                                errorMessage.indexOf(" is not allowed"))
                                        .trim();
                    }
                    throw new OrderException(message);
                } else if (errorMessage.contains("not found")) {
                    throw new EntityNotFoundException("Order", orderId);
                } else if (errorMessage.contains("only for delivery orders")) {
                    throw new OrderException(
                            "Statuses 'delivering' and 'delivered' can only be used for delivery orders");
                }
            }
            log.error("Error updating order status", e);
            throw new OrderException(
                    "Failed to update order status: " + (errorMessage != null ? errorMessage : e.getMessage()), e);
        } catch (Exception e) {
            log.error("Error updating order status", e);
            throw new OrderException("Failed to update order status: " + e.getMessage(), e);
        }
    }

    @Transactional
    public Order updateOrderPaymentMethod(Integer orderId, PaymentMethod paymentMethod) {
        Order order = getOrderById(orderId);
        if (order.getPayment() != null) {
            throw new OrderException("Cannot change payment method after payment is processed");
        }

        order.setPaymentMethod(paymentMethod);
        Order saved = orderRepository.save(order);
        log.info(
                "Order {} payment method updated to {}",
                orderId,
                paymentMethod != null ? paymentMethod.getValue() : null);
        return saved;
    }

    public List<KitchenQueueItem> getKitchenQueue() {
        List<Object[]> results = orderRepository.callGetKitchenQueue();
        List<KitchenQueueItem> queue = new ArrayList<>();

        for (Object[] row : results) {
            try {
                Integer orderId = row[0] != null ? ((Number) row[0]).intValue() : null;
                OffsetDateTime createdAt = extractCreatedAt(row[1]);
                String status = row[2] != null ? row[2].toString() : null;
                OffsetDateTime preparingAt = extractCreatedAt(row[3]);
                OffsetDateTime readyAt = extractCreatedAt(row[4]);
                Integer cookingDurationSeconds = row[5] != null ? ((Number) row[5]).intValue() : null;
                String itemsJson = row[6] != null ? row[6].toString() : null;

                List<OrderItemInfo> items = parseItemsJson(itemsJson);

                KitchenQueueItem item = new KitchenQueueItem();
                item.setOrderId(orderId);
                item.setCreatedAt(createdAt);
                item.setStatus(status);
                item.setPreparingAt(preparingAt);
                item.setReadyAt(readyAt);
                item.setCookingDurationSeconds(cookingDurationSeconds);
                item.setItems(items);

                queue.add(item);
            } catch (Exception e) {
                log.error("Error parsing kitchen queue item: {}", Arrays.toString(row), e);
            }
        }

        return queue;
    }

    private OffsetDateTime extractCreatedAt(Object value) {
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atOffset(ZoneOffset.UTC);
        }
        if (value instanceof OffsetDateTime odt) {
            return odt;
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt.atOffset(ZoneOffset.UTC);
        }
        return null;
    }

    private List<OrderItemInfo> parseItemsJson(String itemsJson) {
        try {
            if (itemsJson == null || itemsJson.trim().isEmpty() || itemsJson.equals("null")) {
                return new ArrayList<>();
            }

            List<Map<String, Object>> itemsList =
                    objectMapper.readValue(itemsJson, new TypeReference<List<Map<String, Object>>>() {});

            List<OrderItemInfo> items = new ArrayList<>();
            for (Map<String, Object> itemMap : itemsList) {
                OrderItemInfo itemInfo = new OrderItemInfo();
                itemInfo.setMenuItemId(((Number) itemMap.get("menu_item_id")).intValue());
                itemInfo.setName((String) itemMap.get("name"));
                itemInfo.setQuantity(((Number) itemMap.get("quantity")).intValue());
                itemInfo.setNote((String) itemMap.get("note"));
                items.add(itemInfo);
            }

            return items;
        } catch (JsonProcessingException e) {
            log.error("Error parsing items JSON: {}", itemsJson, e);
            return new ArrayList<>();
        }
    }

    public Order getOrderById(Integer orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new EntityNotFoundException("Order", orderId));
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public List<Order> getOrdersByStatus(String status) {
        return orderRepository.findByStatus(status);
    }

    public List<Order> getOrdersByClient(Integer clientId) {
        return orderRepository.findByClientId(clientId);
    }

    public List<Order> getOrdersByStatusAndClient(String status, Integer clientId) {
        return orderRepository.findByClientId(clientId).stream()
                .filter(order -> order.getStatus() != null
                        && order.getStatus().getValue().equals(status))
                .collect(java.util.stream.Collectors.toList());
    }

    public List<OrderItem> getOrderItems(Integer orderId) {
        getOrderById(orderId);
        return orderItemRepository.findDetailedByOrderId(orderId);
    }

    public List<OrderItem> getOrderItemsBatch(List<Integer> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        return orderItemRepository.findDetailedByOrderIds(orderIds);
    }

    @Transactional
    public Order assignCourierToOrder(Integer orderId, Integer courierId) {
        Order order = getOrderById(orderId);
        if (order.getType() == null
                || !"delivery".equalsIgnoreCase(order.getType().getValue())) {
            throw new OrderException("Courier can only be assigned to delivery orders");
        }
        if (order.getStatus() == OrderStatus.DELIVERED
                || order.getStatus() == OrderStatus.COMPLETED
                || order.getStatus() == OrderStatus.CANCELLED) {
            throw new OrderException("Courier cannot be assigned to closed delivery orders");
        }

        Courier courier = null;
        if (courierId != null) {
            courier = courierRepository
                    .findById(courierId)
                    .orElseThrow(() -> new EntityNotFoundException("Courier", courierId));

            if (Boolean.FALSE.equals(courier.getAvailable())) {
                throw new OrderException("Courier is not available");
            }

            Integer currentCourierId =
                    order.getCourier() != null ? order.getCourier().getId() : null;
            if (currentCourierId == null || !currentCourierId.equals(courierId)) {
                boolean busy = orderRepository.existsByCourier_IdAndStatusInAndIdNot(
                        courierId, COURIER_BUSY_STATUSES, orderId);
                if (busy) {
                    throw new OrderException("Courier is busy");
                }
            }
        }

        order.setCourier(courier);
        Order saved = orderRepository.save(order);
        log.info("Order {} courier set to {}", orderId, courierId);
        return saved;
    }
}
