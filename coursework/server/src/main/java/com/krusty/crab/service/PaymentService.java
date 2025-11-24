package com.krusty.crab.service;

import com.krusty.crab.entity.Payment;
import com.krusty.crab.entity.enums.PaymentMethod;
import com.krusty.crab.exception.EntityNotFoundException;
import com.krusty.crab.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
    
    private final PaymentRepository paymentRepository;
    
    @Transactional
    public Integer processPayment(Integer orderId, PaymentMethod method) {
        // Call PostgreSQL function
        Integer paymentId = paymentRepository.callProcessPayment(orderId, method.getValue());
        log.info("Payment processed successfully with ID: {} for order: {}", paymentId, orderId);
        return paymentId;
    }
    
    public Payment getPaymentByOrderId(Integer orderId) {
        return paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new EntityNotFoundException("Payment", orderId));
    }
    
    public boolean paymentExists(Integer orderId) {
        return paymentRepository.existsByOrderId(orderId);
    }
}

