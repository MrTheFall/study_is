package com.krusty.crab.controller;

import com.krusty.crab.api.PaymentsApi;
import com.krusty.crab.dto.generated.CashPaymentRequest;
import com.krusty.crab.dto.generated.PaymentRequest;
import com.krusty.crab.entity.Order;
import com.krusty.crab.entity.enums.PaymentMethod;
import com.krusty.crab.exception.EntityNotFoundException;
import com.krusty.crab.exception.ValidationException;
import com.krusty.crab.mapper.PaymentMapper;
import com.krusty.crab.repository.OrderRepository;
import com.krusty.crab.security.UserPrincipal;
import com.krusty.crab.service.PaymentService;
import com.krusty.crab.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class PaymentsController implements PaymentsApi {
    
    private final PaymentService paymentService;
    private final PaymentMapper paymentMapper;
    private final OrderRepository orderRepository;
    
    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.Payment> processPayment(PaymentRequest paymentRequest) {
        log.info("Processing payment for order: {} with method: {}", paymentRequest.getOrderId(), paymentRequest.getMethod());
        Order order = orderRepository.findById(paymentRequest.getOrderId())
            .orElseThrow(() -> new EntityNotFoundException("Order", paymentRequest.getOrderId()));

        PaymentMethod method = PaymentMethod.fromValue(paymentRequest.getMethod().getValue());
        validatePaymentAccess(order, method);
        boolean simulateFailure = Boolean.TRUE.equals(paymentRequest.getSimulateFailure());
        Integer paymentId = paymentService.processPayment(paymentRequest.getOrderId(), method, simulateFailure);
        com.krusty.crab.entity.Payment payment = paymentService.getPaymentByOrderId(paymentRequest.getOrderId());
        com.krusty.crab.dto.generated.Payment dto = paymentMapper.toDto(payment);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
    
    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.ChangeResponse> processCashPayment(CashPaymentRequest cashPaymentRequest) {
        log.info("Processing cash payment for order: {} with amount: {}", 
            cashPaymentRequest.getOrderId(), cashPaymentRequest.getAmountReceived());
        Order order = orderRepository.findById(cashPaymentRequest.getOrderId())
            .orElseThrow(() -> new EntityNotFoundException("Order", cashPaymentRequest.getOrderId()));
        validatePaymentAccess(order, PaymentMethod.CASH);
        com.krusty.crab.dto.generated.ChangeResponse response = paymentService.processCashPayment(
            cashPaymentRequest.getOrderId(), 
            cashPaymentRequest.getAmountReceived()
        );
        return ResponseEntity.ok(response);
    }
    
    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.Payment> getPaymentByOrderId(Integer orderId) {
        log.info("Getting payment for order: {}", orderId);
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new EntityNotFoundException("Order", orderId));
        validatePaymentViewAccess(order);
        com.krusty.crab.entity.Payment payment = paymentService.getPaymentByOrderId(orderId);
        com.krusty.crab.dto.generated.Payment dto = paymentMapper.toDto(payment);
        return ResponseEntity.ok(dto);
    }

    private void validatePaymentAccess(Order order, PaymentMethod method) {
        UserPrincipal user = SecurityUtil.getCurrentUser();

        if (order.getPaymentMethod() != null && order.getPaymentMethod() != method) {
            throw new ValidationException("Payment method does not match the order");
        }

        String orderStatus = order.getStatus() != null ? order.getStatus().getValue() : null;
        String orderType = order.getType() != null ? order.getType().getValue() : null;

        if (method == PaymentMethod.ONLINE) {
            if (orderStatus == null || !"pending".equalsIgnoreCase(orderStatus)) {
                throw new ValidationException("Online payment is only allowed for pending orders");
            }
        } else {
            if ("delivery".equalsIgnoreCase(orderType)) {
                if (orderStatus == null || !"delivered".equalsIgnoreCase(orderStatus)) {
                    throw new ValidationException("Pay on receipt is only allowed after delivery");
                }
            } else {
                if (orderStatus == null || !"pending".equalsIgnoreCase(orderStatus)) {
                    throw new ValidationException("Payment is only allowed for pending orders");
                }
            }
        }

        if ("CLIENT".equals(user.getUserType())) {
            Integer orderClientId = order.getClient() != null ? order.getClient().getId() : null;
            if (orderClientId == null || !orderClientId.equals(user.getUserId())) {
                throw new ValidationException("Access denied");
            }
            if (method != PaymentMethod.ONLINE) {
                throw new ValidationException("Clients can only pay online");
            }
            return;
        }

        if ("EMPLOYEE".equals(user.getUserType())) {
            requireEmployeeRoleAny("Cashier", "Manager");
            if (method == PaymentMethod.ONLINE) {
                throw new ValidationException("Employees cannot process online payments");
            }
            return;
        }

        throw new ValidationException("Access denied");
    }

    private void validatePaymentViewAccess(Order order) {
        UserPrincipal user = SecurityUtil.getCurrentUser();

        if ("CLIENT".equals(user.getUserType())) {
            Integer orderClientId = order.getClient() != null ? order.getClient().getId() : null;
            if (orderClientId == null || !orderClientId.equals(user.getUserId())) {
                throw new ValidationException("Access denied");
            }
            return;
        }

        if ("EMPLOYEE".equals(user.getUserType())) {
            requireEmployeeRoleAny("Cashier", "Manager");
            return;
        }

        throw new ValidationException("Access denied");
    }

    private void requireEmployeeRoleAny(String... allowedRoles) {
        UserPrincipal user = SecurityUtil.getCurrentUser();

        if (!"EMPLOYEE".equals(user.getUserType())) {
            throw new ValidationException("Only employees can perform this action");
        }

        String role = user.getRole();
        if (role == null) {
            throw new ValidationException("Access denied");
        }

        for (String allowed : allowedRoles) {
            if (allowed.equals(role)) {
                return;
            }
        }

        throw new ValidationException("Access denied");
    }
}
