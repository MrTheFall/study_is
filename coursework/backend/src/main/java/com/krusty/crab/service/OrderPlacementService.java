package com.krusty.crab.service;

import com.krusty.crab.dto.generated.PlaceOrderRequest;
import com.krusty.crab.exception.ValidationException;
import com.krusty.crab.security.SecurityContext;
import com.krusty.crab.security.UserPrincipal;
import com.krusty.crab.security.UserType;
import com.krusty.crab.util.AuditActions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderPlacementService {

    private final OrderService orderService;
    private final EmployeeActionLogService actionLogService;

    public Integer placeOrder(PlaceOrderRequest request) {
        UserPrincipal user = SecurityContext.getCurrentUser();
        validatePlacement(request, user);

        Integer createdByEmployeeId = user.getUserType() == UserType.EMPLOYEE ? user.getUserId() : null;
        Integer orderId = orderService.placeOrder(request, createdByEmployeeId);

        if (user.getUserType() == UserType.EMPLOYEE) {
            String details = String.format(
                    "clientId=%s, type=%s, paymentMethod=%s",
                    request.getClientId(),
                    request.getType() != null ? request.getType().getValue() : null,
                    request.getPaymentMethod() != null
                            ? request.getPaymentMethod().getValue()
                            : null);
            actionLogService.logOrderAction(user.getUserId(), AuditActions.ORDER_CREATE, orderId, details);
        }

        return orderId;
    }

    private void validatePlacement(PlaceOrderRequest request, UserPrincipal user) {
        if (user.getUserType() == UserType.CLIENT
                && request.getClientId() != null
                && !request.getClientId().equals(user.getUserId())) {
            throw new AccessDeniedException("Clients can only place orders for themselves");
        }
        if (request.getType() != null
                && "delivery".equalsIgnoreCase(request.getType().getValue())
                && request.getPaymentMethod() == null) {
            throw new ValidationException("paymentMethod is required for delivery orders");
        }
    }
}
