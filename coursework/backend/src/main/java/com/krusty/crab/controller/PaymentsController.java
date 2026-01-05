package com.krusty.crab.controller;

import com.krusty.crab.api.PaymentsApi;
import com.krusty.crab.dto.generated.CashPaymentRequest;
import com.krusty.crab.dto.generated.OnlinePaymentStartRequest;
import com.krusty.crab.dto.generated.OnlinePaymentStartResponse;
import com.krusty.crab.dto.generated.OnlinePaymentStatus;
import com.krusty.crab.dto.generated.PaymentRequest;
import com.krusty.crab.entity.OnlinePaymentSession;
import com.krusty.crab.mapper.PaymentMapper;
import com.krusty.crab.service.PaymentWorkflowService;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class PaymentsController implements PaymentsApi {

    private final PaymentMapper paymentMapper;
    private final PaymentWorkflowService paymentWorkflowService;

    @Override
    @PreAuthorize("hasRole('CLIENT') or hasRole('Cashier') or hasRole('Manager')")
    public ResponseEntity<List<com.krusty.crab.dto.generated.Payment>> getPayments(
            Boolean success, OffsetDateTime from, OffsetDateTime to, Integer limit, Integer offset) {
        log.info(
                "Getting payments, success: {}, from: {}, to: {}, limit: {}, offset: {}",
                success,
                from,
                to,
                limit,
                offset);

        List<com.krusty.crab.entity.Payment> payments =
                paymentWorkflowService.listPayments(success, from, to, limit, offset);
        return ResponseEntity.ok(paymentMapper.toDtoList(payments));
    }

    @Override
    @PreAuthorize("hasRole('CLIENT') or hasRole('Cashier') or hasRole('Manager')")
    public ResponseEntity<com.krusty.crab.dto.generated.Payment> processPayment(PaymentRequest paymentRequest) {
        log.info(
                "Processing payment for order: {} with method: {}",
                paymentRequest.getOrderId(),
                paymentRequest.getMethod());
        com.krusty.crab.dto.generated.Payment dto =
                paymentMapper.toDto(paymentWorkflowService.processPayment(paymentRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Override
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<OnlinePaymentStartResponse> startOnlinePayment(
            OnlinePaymentStartRequest onlinePaymentStartRequest) {
        Integer orderId = onlinePaymentStartRequest.getOrderId();
        log.info("Starting online payment for order: {}", orderId);

        String baseUrl = org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentContextPath()
                .build()
                .toUriString();

        OnlinePaymentSession session = paymentWorkflowService.startOnlinePayment(onlinePaymentStartRequest, baseUrl);

        OnlinePaymentStartResponse response = new OnlinePaymentStartResponse();
        response.setSessionId(session.getId().toString());
        response.setOrderId(orderId);
        response.setAmount(session.getAmount());
        response.setStatus(OnlinePaymentStatus.fromValue(session.getStatus().getValue()));
        response.setRedirectUrl(session.getRedirectUrl());
        if (session.getExpiresAt() != null) {
            response.setExpiresAt(session.getExpiresAt().atOffset(java.time.ZoneOffset.UTC));
        }

        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasRole('Cashier') or hasRole('Manager')")
    public ResponseEntity<com.krusty.crab.dto.generated.ChangeResponse> processCashPayment(
            CashPaymentRequest cashPaymentRequest) {
        log.info(
                "Processing cash payment for order: {} with amount: {}",
                cashPaymentRequest.getOrderId(),
                cashPaymentRequest.getAmountReceived());
        com.krusty.crab.dto.generated.ChangeResponse response =
                paymentWorkflowService.processCashPayment(cashPaymentRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasRole('CLIENT') or hasRole('Cashier') or hasRole('Manager')")
    public ResponseEntity<com.krusty.crab.dto.generated.Payment> getPaymentByOrderId(Integer orderId) {
        log.info("Getting payment for order: {}", orderId);
        com.krusty.crab.entity.Payment payment = paymentWorkflowService.getPaymentByOrderId(orderId);
        com.krusty.crab.dto.generated.Payment dto = paymentMapper.toDto(payment);
        return ResponseEntity.ok(dto);
    }
}
