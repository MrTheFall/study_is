package com.krusty.crab.service;

import com.krusty.crab.dto.generated.CashPaymentRequest;
import com.krusty.crab.dto.generated.ChangeResponse;
import com.krusty.crab.dto.generated.OnlinePaymentStartRequest;
import com.krusty.crab.dto.generated.PaymentRequest;
import com.krusty.crab.entity.OnlinePaymentSession;
import com.krusty.crab.entity.Order;
import com.krusty.crab.entity.Payment;
import com.krusty.crab.entity.enums.PaymentMethod;
import com.krusty.crab.exception.EntityNotFoundException;
import com.krusty.crab.exception.ValidationException;
import com.krusty.crab.repository.OrderRepository;
import com.krusty.crab.security.SecurityContext;
import com.krusty.crab.security.UserPrincipal;
import com.krusty.crab.security.UserType;
import com.krusty.crab.util.AuditActions;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentWorkflowService {

    private final PaymentService paymentService;
    private final OrderRepository orderRepository;
    private final OnlinePaymentService onlinePaymentService;
    private final EmployeeActionLogService actionLogService;

    public List<Payment> listPayments(
            Boolean success, OffsetDateTime from, OffsetDateTime to, Integer limit, Integer offset) {
        UserPrincipal user = SecurityContext.getCurrentUser();
        Integer clientId = null;

        if (user.getUserType() == UserType.CLIENT) {
            clientId = user.getUserId();
        } else if (user.getUserType() == UserType.EMPLOYEE) {
            requireEmployeeRoleAny("Cashier", "Manager");
        } else {
            throw new AccessDeniedException("Access denied");
        }

        return paymentService.listPayments(clientId, success, from, to, limit, offset);
    }

    public Payment processPayment(PaymentRequest paymentRequest) {
        Order order = orderRepository
                .findById(paymentRequest.getOrderId())
                .orElseThrow(() -> new EntityNotFoundException("Order", paymentRequest.getOrderId()));

        PaymentMethod method =
                PaymentMethod.fromValue(paymentRequest.getMethod().getValue());
        if (method == PaymentMethod.ONLINE) {
            throw new ValidationException("Online payment must be initiated via /payments/online/start");
        }

        validatePaymentAccess(order, method);
        boolean shouldSimulateFailure = Boolean.TRUE.equals(paymentRequest.getShouldSimulateFailure());
        paymentService.processPayment(paymentRequest.getOrderId(), method, shouldSimulateFailure);

        Payment payment = paymentService.getPaymentByOrderId(paymentRequest.getOrderId());
        UserPrincipal user = SecurityContext.getCurrentUser();
        if (user.getUserType() == UserType.EMPLOYEE) {
            String details = String.format("method=%s, amount=%s", method.getValue(), order.getTotalAmount());
            actionLogService.logOrderAction(user.getUserId(), AuditActions.PAYMENT_PROCESS, order.getId(), details);
        }
        return payment;
    }

    public ChangeResponse processCashPayment(CashPaymentRequest cashPaymentRequest) {
        Order order = orderRepository
                .findById(cashPaymentRequest.getOrderId())
                .orElseThrow(() -> new EntityNotFoundException("Order", cashPaymentRequest.getOrderId()));
        validatePaymentAccess(order, PaymentMethod.CASH);
        ChangeResponse response = paymentService.processCashPayment(
                cashPaymentRequest.getOrderId(), cashPaymentRequest.getAmountReceived());

        UserPrincipal user = SecurityContext.getCurrentUser();
        if (user.getUserType() == UserType.EMPLOYEE) {
            String details = String.format(
                    "method=%s, amountReceived=%s",
                    PaymentMethod.CASH.getValue(), cashPaymentRequest.getAmountReceived());
            actionLogService.logOrderAction(user.getUserId(), AuditActions.PAYMENT_PROCESS, order.getId(), details);
        }
        return response;
    }

    public Payment getPaymentByOrderId(Integer orderId) {
        Order order =
                orderRepository.findById(orderId).orElseThrow(() -> new EntityNotFoundException("Order", orderId));
        validatePaymentViewAccess(order);
        return paymentService.getPaymentByOrderId(orderId);
    }

    public OnlinePaymentSession startOnlinePayment(OnlinePaymentStartRequest request, String baseUrl) {
        Integer orderId = request.getOrderId();
        Order order =
                orderRepository.findById(orderId).orElseThrow(() -> new EntityNotFoundException("Order", orderId));
        validatePaymentAccess(order, PaymentMethod.ONLINE);

        OnlinePaymentService.CardData cardData = new OnlinePaymentService.CardData(
                request.getCardNumber(), request.getCardExpiry(), request.getCardCvv(), request.getCardHolder());

        boolean shouldSimulateFailure = Boolean.TRUE.equals(request.getShouldSimulateFailure());
        return onlinePaymentService.startPayment(
                order,
                cardData,
                shouldSimulateFailure,
                baseUrl + "/payments/online/return",
                baseUrl + "/payments/online/notify",
                baseUrl);
    }

    private void validatePaymentAccess(Order order, PaymentMethod method) {
        UserPrincipal user = SecurityContext.getCurrentUser();

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

        if (user.getUserType() == UserType.CLIENT) {
            Integer orderClientId =
                    order.getClient() != null ? order.getClient().getId() : null;
            if (orderClientId == null || !orderClientId.equals(user.getUserId())) {
                throw new AccessDeniedException("Access denied");
            }
            if (method != PaymentMethod.ONLINE) {
                throw new AccessDeniedException("Clients can only pay online");
            }
            return;
        }

        if (user.getUserType() == UserType.EMPLOYEE) {
            requireEmployeeRoleAny("Cashier", "Manager");
            if (method == PaymentMethod.ONLINE) {
                throw new AccessDeniedException("Employees cannot process online payments");
            }
            return;
        }

        throw new AccessDeniedException("Access denied");
    }

    private void validatePaymentViewAccess(Order order) {
        UserPrincipal user = SecurityContext.getCurrentUser();

        if (user.getUserType() == UserType.CLIENT) {
            Integer orderClientId =
                    order.getClient() != null ? order.getClient().getId() : null;
            if (orderClientId == null || !orderClientId.equals(user.getUserId())) {
                throw new AccessDeniedException("Access denied");
            }
            return;
        }

        if (user.getUserType() == UserType.EMPLOYEE) {
            requireEmployeeRoleAny("Cashier", "Manager");
            return;
        }

        throw new AccessDeniedException("Access denied");
    }

    private void requireEmployeeRoleAny(String... allowedRoles) {
        UserPrincipal user = SecurityContext.getCurrentUser();

        if (user.getUserType() != UserType.EMPLOYEE) {
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
