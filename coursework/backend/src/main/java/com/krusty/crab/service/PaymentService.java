package com.krusty.crab.service;

import com.krusty.crab.entity.Payment;
import com.krusty.crab.entity.enums.PaymentMethod;
import com.krusty.crab.exception.EntityNotFoundException;
import com.krusty.crab.exception.PaymentException;
import com.krusty.crab.mapper.PaymentMapper;
import com.krusty.crab.repository.OrderRepository;
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
    private final OrderRepository orderRepository;
    private final PaymentMapper paymentMapper;
    
    @Transactional
    public Integer processPayment(Integer orderId, PaymentMethod method) {
        orderRepository.findById(orderId)
            .orElseThrow(() -> new EntityNotFoundException("Order", orderId));
        
        if (paymentRepository.existsByOrderId(orderId)) {
            throw new PaymentException("Payment already exists for order " + orderId);
        }
        
        try {
            Integer paymentId = paymentRepository.callProcessPayment(orderId, method.getValue());
            log.info("Payment processed successfully with ID: {} for order: {}", paymentId, orderId);
            return paymentId;
        } catch (Exception e) {
            throw new PaymentException("Failed to process payment: " + e.getMessage(), e);
        }
    }
    
    public Payment getPaymentByOrderId(Integer orderId) {
        return paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new EntityNotFoundException("Payment", orderId));
    }
    
    public boolean paymentExists(Integer orderId) {
        return paymentRepository.existsByOrderId(orderId);
    }
    
    @Transactional
    public com.krusty.crab.dto.generated.ChangeResponse processCashPayment(Integer orderId, java.math.BigDecimal amountReceived) {
        com.krusty.crab.entity.Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new EntityNotFoundException("Order", orderId));
        
        if (amountReceived.compareTo(order.getTotalAmount()) < 0) {
            throw new PaymentException("Amount received is less than order total");
        }
        
        processPayment(orderId, PaymentMethod.CASH);
        
        return paymentMapper.toChangeResponse(order.getTotalAmount(), amountReceived);
    }
}

