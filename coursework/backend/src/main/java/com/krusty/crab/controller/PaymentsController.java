package com.krusty.crab.controller;

import com.krusty.crab.api.PaymentsApi;
import com.krusty.crab.dto.generated.CashPaymentRequest;
import com.krusty.crab.dto.generated.PaymentRequest;
import com.krusty.crab.entity.enums.PaymentMethod;
import com.krusty.crab.mapper.PaymentMapper;
import com.krusty.crab.service.PaymentService;
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
    
    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.Payment> processPayment(PaymentRequest paymentRequest) {
        log.info("Processing payment for order: {} with method: {}", paymentRequest.getOrderId(), paymentRequest.getMethod());
        PaymentMethod method = PaymentMethod.fromValue(paymentRequest.getMethod().getValue());
        Integer paymentId = paymentService.processPayment(paymentRequest.getOrderId(), method);
        com.krusty.crab.entity.Payment payment = paymentService.getPaymentByOrderId(paymentRequest.getOrderId());
        com.krusty.crab.dto.generated.Payment dto = paymentMapper.toDto(payment);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
    
    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.ChangeResponse> processCashPayment(CashPaymentRequest cashPaymentRequest) {
        log.info("Processing cash payment for order: {} with amount: {}", 
            cashPaymentRequest.getOrderId(), cashPaymentRequest.getAmountReceived());
        com.krusty.crab.dto.generated.ChangeResponse response = paymentService.processCashPayment(
            cashPaymentRequest.getOrderId(), 
            cashPaymentRequest.getAmountReceived()
        );
        return ResponseEntity.ok(response);
    }
    
    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.Payment> getPaymentByOrderId(Integer orderId) {
        log.info("Getting payment for order: {}", orderId);
        com.krusty.crab.entity.Payment payment = paymentService.getPaymentByOrderId(orderId);
        com.krusty.crab.dto.generated.Payment dto = paymentMapper.toDto(payment);
        return ResponseEntity.ok(dto);
    }
}

