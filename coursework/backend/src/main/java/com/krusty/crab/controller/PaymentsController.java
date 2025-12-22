package com.krusty.crab.controller;

import com.krusty.crab.api.PaymentsApi;
import com.krusty.crab.dto.generated.CashPaymentRequest;
import com.krusty.crab.dto.generated.PaymentRequest;
import com.krusty.crab.entity.Order;
import com.krusty.crab.entity.OnlinePaymentSession;
import com.krusty.crab.entity.enums.PaymentMethod;
import com.krusty.crab.exception.EntityNotFoundException;
import com.krusty.crab.exception.ValidationException;
import com.krusty.crab.mapper.PaymentMapper;
import com.krusty.crab.repository.OrderRepository;
import com.krusty.crab.security.UserPrincipal;
import com.krusty.crab.service.EmployeeActionLogService;
import com.krusty.crab.service.OnlinePaymentService;
import com.krusty.crab.service.PaymentService;
import com.krusty.crab.util.AuditActions;
import com.krusty.crab.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class PaymentsController implements PaymentsApi {

    private final PaymentService paymentService;
    private final PaymentMapper paymentMapper;
    private final OrderRepository orderRepository;
    private final OnlinePaymentService onlinePaymentService;
    private final EmployeeActionLogService actionLogService;

    @Override
    @PreAuthorize("hasRole('CLIENT') or hasRole('Cashier') or hasRole('Manager')")
    public ResponseEntity<List<com.krusty.crab.dto.generated.Payment>> getPayments(
        Boolean success,
        OffsetDateTime from,
        OffsetDateTime to,
        Integer limit,
        Integer offset
    ) {
        log.info("Getting payments, success: {}, from: {}, to: {}, limit: {}, offset: {}", success, from, to, limit, offset);

        UserPrincipal user = SecurityUtil.getCurrentUser();
        Integer clientId = null;

        if ("CLIENT".equals(user.getUserType())) {
            clientId = user.getUserId();
        } else if ("EMPLOYEE".equals(user.getUserType())) {
            requireEmployeeRoleAny("Cashier", "Manager");
        } else {
            throw new AccessDeniedException("Access denied");
        }

        List<com.krusty.crab.entity.Payment> payments = paymentService.listPayments(clientId, success, from, to, limit, offset);
        return ResponseEntity.ok(paymentMapper.toDtoList(payments));
    }

    @Override
    @PreAuthorize("hasRole('CLIENT') or hasRole('Cashier') or hasRole('Manager')")
    public ResponseEntity<com.krusty.crab.dto.generated.Payment> processPayment(PaymentRequest paymentRequest) {
        log.info("Processing payment for order: {} with method: {}", paymentRequest.getOrderId(), paymentRequest.getMethod());
        Order order = orderRepository.findById(paymentRequest.getOrderId())
            .orElseThrow(() -> new EntityNotFoundException("Order", paymentRequest.getOrderId()));

        PaymentMethod method = PaymentMethod.fromValue(paymentRequest.getMethod().getValue());
        if (method == PaymentMethod.ONLINE) {
            throw new ValidationException("Online payment must be initiated via /payments/online/start");
        }
        validatePaymentAccess(order, method);
        boolean simulateFailure = Boolean.TRUE.equals(paymentRequest.getSimulateFailure());
        Integer paymentId = paymentService.processPayment(paymentRequest.getOrderId(), method, simulateFailure);
        com.krusty.crab.entity.Payment payment = paymentService.getPaymentByOrderId(paymentRequest.getOrderId());
        UserPrincipal user = SecurityUtil.getCurrentUser();
        if ("EMPLOYEE".equals(user.getUserType())) {
            String details = String.format("method=%s, amount=%s", method.getValue(), order.getTotalAmount());
            actionLogService.logAction(
                user.getUserId(),
                AuditActions.PAYMENT_PROCESS,
                "order",
                order.getId(),
                order.getId(),
                null,
                null,
                details
            );
        }
        com.krusty.crab.dto.generated.Payment dto = paymentMapper.toDto(payment);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Override
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<com.krusty.crab.dto.generated.OnlinePaymentStartResponse> startOnlinePayment(
        com.krusty.crab.dto.generated.OnlinePaymentStartRequest onlinePaymentStartRequest
    ) {
        Integer orderId = onlinePaymentStartRequest.getOrderId();
        log.info("Starting online payment for order: {}", orderId);

        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new EntityNotFoundException("Order", orderId));
        validatePaymentAccess(order, PaymentMethod.ONLINE);

        String baseUrl = org.springframework.web.servlet.support.ServletUriComponentsBuilder
            .fromCurrentContextPath()
            .build()
            .toUriString();

        OnlinePaymentService.CardData cardData = new OnlinePaymentService.CardData(
            onlinePaymentStartRequest.getCardNumber(),
            onlinePaymentStartRequest.getCardExpiry(),
            onlinePaymentStartRequest.getCardCvv(),
            onlinePaymentStartRequest.getCardHolder()
        );

        boolean simulateFailure = Boolean.TRUE.equals(onlinePaymentStartRequest.getSimulateFailure());
        OnlinePaymentSession session = onlinePaymentService.startPayment(
            order,
            cardData,
            simulateFailure,
            baseUrl + "/payments/online/return",
            baseUrl + "/payments/online/notify",
            baseUrl
        );

        com.krusty.crab.dto.generated.OnlinePaymentStartResponse response =
            new com.krusty.crab.dto.generated.OnlinePaymentStartResponse();
        response.setSessionId(session.getId().toString());
        response.setOrderId(orderId);
        response.setAmount(session.getAmount());
        response.setStatus(
            com.krusty.crab.dto.generated.OnlinePaymentStatus.fromValue(session.getStatus().getValue())
        );
        response.setRedirectUrl(session.getRedirectUrl());
        if (session.getExpiresAt() != null) {
            response.setExpiresAt(session.getExpiresAt().atOffset(java.time.ZoneOffset.UTC));
        }

        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasRole('Cashier') or hasRole('Manager')")
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
        UserPrincipal user = SecurityUtil.getCurrentUser();
        if ("EMPLOYEE".equals(user.getUserType())) {
            String details = String.format("method=%s, amountReceived=%s", PaymentMethod.CASH.getValue(), cashPaymentRequest.getAmountReceived());
            actionLogService.logAction(
                user.getUserId(),
                AuditActions.PAYMENT_PROCESS,
                "order",
                order.getId(),
                order.getId(),
                null,
                null,
                details
            );
        }
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasRole('CLIENT') or hasRole('Cashier') or hasRole('Manager')")
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
                throw new AccessDeniedException("Access denied");
            }
            if (method != PaymentMethod.ONLINE) {
                throw new AccessDeniedException("Clients can only pay online");
            }
            return;
        }

        if ("EMPLOYEE".equals(user.getUserType())) {
            requireEmployeeRoleAny("Cashier", "Manager");
            if (method == PaymentMethod.ONLINE) {
                throw new AccessDeniedException("Employees cannot process online payments");
            }
            return;
        }

        throw new AccessDeniedException("Access denied");
    }

    private void validatePaymentViewAccess(Order order) {
        UserPrincipal user = SecurityUtil.getCurrentUser();

        if ("CLIENT".equals(user.getUserType())) {
            Integer orderClientId = order.getClient() != null ? order.getClient().getId() : null;
            if (orderClientId == null || !orderClientId.equals(user.getUserId())) {
                throw new AccessDeniedException("Access denied");
            }
            return;
        }

        if ("EMPLOYEE".equals(user.getUserType())) {
            requireEmployeeRoleAny("Cashier", "Manager");
            return;
        }

        throw new AccessDeniedException("Access denied");
    }

    private void requireEmployeeRoleAny(String... allowedRoles) {
        UserPrincipal user = SecurityUtil.getCurrentUser();

        if (!"EMPLOYEE".equals(user.getUserType())) {
            throw new AccessDeniedException("Only employees can perform this action");
        }

        String role = user.getRole();
        if (role == null) {
            throw new AccessDeniedException("Access denied");
        }

        for (String allowed : allowedRoles) {
            if (allowed.equals(role)) {
                return;
            }
        }

        throw new AccessDeniedException("Access denied");
    }
}
