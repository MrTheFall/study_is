package com.krusty.crab.controller;

import com.krusty.crab.api.OrdersApi;
import com.krusty.crab.dto.generated.AssignCourierRequest;
import com.krusty.crab.dto.generated.OrderItemsBatchRequest;
import com.krusty.crab.dto.generated.PlaceOrder201Response;
import com.krusty.crab.dto.generated.PlaceOrderRequest;
import com.krusty.crab.dto.generated.UpdateOrderPaymentMethodRequest;
import com.krusty.crab.dto.generated.UpdateOrderStatusRequest;
import com.krusty.crab.entity.Order;
import com.krusty.crab.entity.OrderItem;
import com.krusty.crab.entity.enums.PaymentMethod;
import com.krusty.crab.exception.ValidationException;
import com.krusty.crab.mapper.OrderItemMapper;
import com.krusty.crab.mapper.OrderMapper;
import com.krusty.crab.repository.OrderRepository;
import com.krusty.crab.security.SecurityContext;
import com.krusty.crab.security.UserPrincipal;
import com.krusty.crab.security.UserType;
import com.krusty.crab.service.EmployeeActionLogService;
import com.krusty.crab.service.OrderService;
import com.krusty.crab.util.AuditActions;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class OrdersController implements OrdersApi {

    private final OrderService orderService;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderRepository orderRepository;
    private final EmployeeActionLogService actionLogService;

    @Override
    public ResponseEntity<PlaceOrder201Response> placeOrder(PlaceOrderRequest placeOrderRequest) {
        log.info("Placing order for client: {}", placeOrderRequest.getClientId());
        UserPrincipal user = SecurityContext.getCurrentUser();
        if (user.getUserType() == UserType.CLIENT
                && placeOrderRequest.getClientId() != null
                && !placeOrderRequest.getClientId().equals(user.getUserId())) {
            throw new AccessDeniedException("Access denied");
        }
        if (placeOrderRequest.getType() != null
                && "delivery".equalsIgnoreCase(placeOrderRequest.getType().getValue())
                && placeOrderRequest.getPaymentMethod() == null) {
            throw new ValidationException("paymentMethod is required for delivery orders");
        }
        Integer createdByEmployeeId = user.getUserType() == UserType.EMPLOYEE ? user.getUserId() : null;
        Integer orderId = orderService.placeOrder(placeOrderRequest, createdByEmployeeId);
        if (user.getUserType() == UserType.EMPLOYEE) {
            String details = String.format(
                    "clientId=%s, type=%s, paymentMethod=%s",
                    placeOrderRequest.getClientId(),
                    placeOrderRequest.getType() != null
                            ? placeOrderRequest.getType().getValue()
                            : null,
                    placeOrderRequest.getPaymentMethod() != null
                            ? placeOrderRequest.getPaymentMethod().getValue()
                            : null);
            actionLogService.logOrderAction(user.getUserId(), AuditActions.ORDER_CREATE, orderId, details);
        }
        PlaceOrder201Response response = orderMapper.toPlaceOrderResponse(orderId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<List<com.krusty.crab.dto.generated.Order>> getAllOrders(
            com.krusty.crab.dto.generated.OrderStatus status, Integer clientId) {
        log.info("Getting all orders, status: {}, clientId: {}", status, clientId);
        UserPrincipal user = SecurityContext.getCurrentUser();
        if (user.getUserType() == UserType.CLIENT) {
            clientId = user.getUserId();
        }
        List<Order> orders;
        if (status != null && clientId != null) {
            orders = orderService.getOrdersByStatusAndClient(status.getValue(), clientId);
        } else if (status != null) {
            orders = orderService.getOrdersByStatus(status.getValue());
        } else if (clientId != null) {
            orders = orderService.getOrdersByClient(clientId);
        } else {
            orders = orderService.getAllOrders();
        }
        return ResponseEntity.ok(orderMapper.toDtoList(orders));
    }

    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.Order> getOrderById(Integer orderId) {
        log.info("Getting order by ID: {}", orderId);
        Order order = orderService.getOrderById(orderId);
        assertOrderAccess(order);
        com.krusty.crab.dto.generated.Order dto = orderMapper.toDto(order);
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.Order> updateOrderStatus(
            Integer orderId, UpdateOrderStatusRequest updateOrderStatusRequest) {
        log.info("Updating order {} status to {}", orderId, updateOrderStatusRequest.getStatus());
        Order order = orderService.getOrderById(orderId);
        assertOrderAccess(order);

        if (updateOrderStatusRequest.getStatus() == null) {
            throw new ValidationException("status is required");
        }

        UserPrincipal user = SecurityContext.getCurrentUser();
        String currentStatus = order.getStatus() != null ? order.getStatus().getValue() : null;
        com.krusty.crab.dto.generated.OrderStatus requestedStatus = updateOrderStatusRequest.getStatus();

        if (user.getUserType() == UserType.CLIENT) {
            if (requestedStatus != com.krusty.crab.dto.generated.OrderStatus.CANCELLED) {
                throw new AccessDeniedException("Clients can only cancel their orders");
            }
            if (currentStatus == null || !"pending".equalsIgnoreCase(currentStatus)) {
                throw new ValidationException("Client can only cancel pending orders");
            }
        } else if (user.getUserType() == UserType.EMPLOYEE) {
            String role = user.getRole();
            if (role == null) {
                throw new AccessDeniedException("Access denied");
            }

            if ("Cook".equals(role)) {
                if (requestedStatus != com.krusty.crab.dto.generated.OrderStatus.PREPARING
                        && requestedStatus != com.krusty.crab.dto.generated.OrderStatus.READY) {
                    throw new AccessDeniedException("Cook can only set statuses preparing/ready");
                }
            } else if ("Cashier".equals(role)) {
                if (requestedStatus != com.krusty.crab.dto.generated.OrderStatus.CONFIRMED
                        && requestedStatus != com.krusty.crab.dto.generated.OrderStatus.CANCELLED
                        && requestedStatus != com.krusty.crab.dto.generated.OrderStatus.DELIVERING
                        && requestedStatus != com.krusty.crab.dto.generated.OrderStatus.DELIVERED
                        && requestedStatus != com.krusty.crab.dto.generated.OrderStatus.COMPLETED) {
                    throw new AccessDeniedException("Cashier cannot set this status");
                }

                if (requestedStatus == com.krusty.crab.dto.generated.OrderStatus.CANCELLED) {
                    if (currentStatus == null
                            || (!"pending".equalsIgnoreCase(currentStatus)
                                    && !"confirmed".equalsIgnoreCase(currentStatus))) {
                        throw new ValidationException("Cashier can only cancel pending/confirmed orders");
                    }
                }
            } else if (!"Manager".equals(role)) {
                throw new AccessDeniedException("Access denied");
            }

            if ((("Cashier".equals(role) || "Manager".equals(role))
                            && requestedStatus == com.krusty.crab.dto.generated.OrderStatus.CONFIRMED
                            && (currentStatus != null && "pending".equalsIgnoreCase(currentStatus)))
                    && order.getPaymentMethod() == PaymentMethod.ONLINE
                    && order.getPayment() == null) {
                throw new ValidationException("Online orders can only be confirmed after successful payment");
            }

            if ((("Cashier".equals(role) || "Manager".equals(role))
                            && requestedStatus == com.krusty.crab.dto.generated.OrderStatus.CONFIRMED
                            && (currentStatus != null && "pending".equalsIgnoreCase(currentStatus)))
                    && order.getType() != null
                    && !"delivery".equalsIgnoreCase(order.getType().getValue())
                    && (order.getPaymentMethod() == PaymentMethod.CASH
                            || order.getPaymentMethod() == PaymentMethod.CARD)
                    && order.getPayment() == null) {
                throw new ValidationException("Orders must be paid before confirmation");
            }

            if ((("Cashier".equals(role) || "Manager".equals(role))
                            && requestedStatus == com.krusty.crab.dto.generated.OrderStatus.COMPLETED)
                    && order.getType() != null
                    && "delivery".equalsIgnoreCase(order.getType().getValue())
                    && (order.getPaymentMethod() == PaymentMethod.CASH
                            || order.getPaymentMethod() == PaymentMethod.CARD)
                    && order.getPayment() == null) {
                throw new ValidationException("Delivery orders with pay on receipt must be paid before completion");
            }
        } else {
            throw new AccessDeniedException("Access denied");
        }

        com.krusty.crab.entity.enums.OrderStatus newStatus =
                com.krusty.crab.entity.enums.OrderStatus.fromValue(requestedStatus.getValue());
        Integer acceptedByEmployeeId = null;
        if (user.getUserType() == UserType.EMPLOYEE
                && requestedStatus == com.krusty.crab.dto.generated.OrderStatus.CONFIRMED
                && ("Cashier".equals(user.getRole()) || "Manager".equals(user.getRole()))) {
            acceptedByEmployeeId = user.getUserId();
        }
        orderService.updateOrderStatus(orderId, newStatus, acceptedByEmployeeId);
        if (user.getUserType() == UserType.EMPLOYEE
                && (currentStatus == null || !currentStatus.equalsIgnoreCase(newStatus.getValue()))) {
            actionLogService.logOrderAction(
                    user.getUserId(),
                    AuditActions.ORDER_STATUS_CHANGE,
                    orderId,
                    currentStatus,
                    newStatus.getValue());
        }
        Order updatedOrder = orderService.getOrderById(orderId);
        com.krusty.crab.dto.generated.Order dto = orderMapper.toDto(updatedOrder);
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.Order> updateOrderPaymentMethod(
            Integer orderId, UpdateOrderPaymentMethodRequest updateOrderPaymentMethodRequest) {
        log.info("Updating order {} payment method to {}", orderId, updateOrderPaymentMethodRequest.getPaymentMethod());
        Order order = orderService.getOrderById(orderId);
        assertOrderAccess(order);

        if (updateOrderPaymentMethodRequest.getPaymentMethod() == null) {
            throw new ValidationException("paymentMethod is required");
        }

        String currentStatus = order.getStatus() != null ? order.getStatus().getValue() : null;
        if (currentStatus == null || !"pending".equalsIgnoreCase(currentStatus)) {
            throw new ValidationException("Payment method can only be changed for pending orders");
        }

        UserPrincipal user = SecurityContext.getCurrentUser();
        if (user.getUserType() == UserType.CLIENT) {
            // ok (assertOrderAccess already checked ownership)
        } else if (user.getUserType() == UserType.EMPLOYEE) {
            String role = user.getRole();
            if (!"Cashier".equals(role) && !"Manager".equals(role)) {
                throw new AccessDeniedException("Access denied");
            }
            if (updateOrderPaymentMethodRequest.getPaymentMethod()
                    == com.krusty.crab.dto.generated.PaymentMethod.ONLINE) {
                throw new AccessDeniedException("Employees cannot set payment method to online");
            }
        } else {
            throw new AccessDeniedException("Access denied");
        }

        PaymentMethod newMethod = PaymentMethod.fromValue(
                updateOrderPaymentMethodRequest.getPaymentMethod().getValue());
        Order updated = orderService.updateOrderPaymentMethod(orderId, newMethod);
        if (user.getUserType() == UserType.EMPLOYEE) {
            String prevMethod =
                    order.getPaymentMethod() != null ? order.getPaymentMethod().getValue() : null;
            if (prevMethod == null || !prevMethod.equalsIgnoreCase(newMethod.getValue())) {
                actionLogService.logOrderAction(
                        user.getUserId(),
                        AuditActions.ORDER_PAYMENT_METHOD_CHANGE,
                        orderId,
                        prevMethod,
                        newMethod.getValue());
            }
        }
        return ResponseEntity.ok(orderMapper.toDto(updated));
    }

    @Override
    public ResponseEntity<List<com.krusty.crab.dto.generated.OrderItem>> getOrderItems(Integer orderId) {
        log.info("Getting order items for order: {}", orderId);
        Order order = orderService.getOrderById(orderId);
        assertOrderAccess(order);

        List<OrderItem> items = orderService.getOrderItems(orderId);
        return ResponseEntity.ok(orderItemMapper.toDtoList(items));
    }

    @Override
    public ResponseEntity<List<com.krusty.crab.dto.generated.OrderItem>> getOrderItemsBatch(
            OrderItemsBatchRequest orderItemsBatchRequest) {
        List<Integer> orderIds = orderItemsBatchRequest != null ? orderItemsBatchRequest.getOrderIds() : null;
        if (orderIds == null || orderIds.isEmpty()) {
            throw new ValidationException("orderIds is required");
        }

        List<Integer> normalized =
                orderIds.stream().filter(Objects::nonNull).distinct().limit(201).toList();

        if (normalized.isEmpty()) {
            throw new ValidationException("orderIds is required");
        }
        if (normalized.size() > 200) {
            throw new ValidationException("Too many orderIds (max 200)");
        }

        UserPrincipal user = SecurityContext.getCurrentUser();
        if (user.getUserType() == UserType.CLIENT) {
            long ownedCount = orderRepository.countOwnedByClient(user.getUserId(), normalized);
            if (ownedCount != normalized.size()) {
                throw new AccessDeniedException("Access denied");
            }
        }

        List<OrderItem> items = orderService.getOrderItemsBatch(normalized);
        return ResponseEntity.ok(orderItemMapper.toDtoList(items));
    }

    @Override
    @PreAuthorize("hasRole('Manager') or hasRole('Cashier')")
    public ResponseEntity<com.krusty.crab.dto.generated.Order> assignCourierToOrder(
            Integer orderId, AssignCourierRequest assignCourierRequest) {
        log.info("Assigning courier {} to order {}", assignCourierRequest.getCourierId(), orderId);
        UserPrincipal user = SecurityContext.getCurrentUser();
        Integer previousCourierId = null;
        if (user.getUserType() == UserType.EMPLOYEE) {
            Order before = orderService.getOrderById(orderId);
            if (before != null && before.getCourier() != null) {
                previousCourierId = before.getCourier().getId();
            }
        }
        Order updated = orderService.assignCourierToOrder(orderId, assignCourierRequest.getCourierId());
        if (user.getUserType() == UserType.EMPLOYEE) {
            String fromValue = previousCourierId != null ? previousCourierId.toString() : null;
            String toValue = assignCourierRequest.getCourierId() != null
                    ? assignCourierRequest.getCourierId().toString()
                    : null;
            actionLogService.logOrderAction(
                    user.getUserId(),
                    AuditActions.ORDER_COURIER_ASSIGN,
                    orderId,
                    fromValue,
                    toValue);
        }
        return ResponseEntity.ok(orderMapper.toDto(updated));
    }

    private void assertOrderAccess(Order order) {
        UserPrincipal user = SecurityContext.getCurrentUser();
        if (user.getUserType() == UserType.CLIENT) {
            Integer orderClientId =
                    order.getClient() != null ? order.getClient().getId() : null;
            if (orderClientId == null || !orderClientId.equals(user.getUserId())) {
                throw new AccessDeniedException("Access denied");
            }
        }
    }
}
