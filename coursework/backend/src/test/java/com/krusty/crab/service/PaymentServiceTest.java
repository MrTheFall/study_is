package com.krusty.crab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krusty.crab.dto.generated.ChangeResponse;
import com.krusty.crab.entity.Order;
import com.krusty.crab.entity.Payment;
import com.krusty.crab.entity.enums.PaymentMethod;
import com.krusty.crab.exception.EntityNotFoundException;
import com.krusty.crab.exception.PaymentException;
import com.krusty.crab.mapper.PaymentMapper;
import com.krusty.crab.repository.OrderRepository;
import com.krusty.crab.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void processPayment_throwsWhenOrderMissing() {
        when(orderRepository.findById(10)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.processPayment(10, PaymentMethod.CASH, false))
                .isInstanceOf(EntityNotFoundException.class);

        verify(paymentRepository, never()).existsByOrderId(any());
    }

    @Test
    void processPayment_throwsWhenPaymentAlreadyExists() {
        when(orderRepository.findById(10)).thenReturn(Optional.of(new Order()));
        when(paymentRepository.existsByOrderId(10)).thenReturn(true);

        assertThatThrownBy(() -> paymentService.processPayment(10, PaymentMethod.CASH, false))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("Payment already exists");
    }

    @Test
    void processPayment_throwsOnOnlineFailure() {
        when(orderRepository.findById(10)).thenReturn(Optional.of(new Order()));
        when(paymentRepository.existsByOrderId(10)).thenReturn(false);

        assertThatThrownBy(() -> paymentService.processPayment(10, PaymentMethod.ONLINE, true))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("Online payment failed");
    }

    @Test
    void processPayment_wrapsDataAccessException() {
        when(orderRepository.findById(10)).thenReturn(Optional.of(new Order()));
        when(paymentRepository.existsByOrderId(10)).thenReturn(false);
        when(paymentRepository.callProcessPayment(10, PaymentMethod.CASH.getValue()))
                .thenThrow(new DataAccessResourceFailureException("ERROR: db down"));

        assertThatThrownBy(() -> paymentService.processPayment(10, PaymentMethod.CASH, false))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("db down");
    }

    @Test
    void processPayment_returnsPaymentIdOnSuccess() {
        when(orderRepository.findById(10)).thenReturn(Optional.of(new Order()));
        when(paymentRepository.existsByOrderId(10)).thenReturn(false);
        when(paymentRepository.callProcessPayment(10, PaymentMethod.CARD.getValue()))
                .thenReturn(99);

        Integer paymentId = paymentService.processPayment(10, PaymentMethod.CARD, false);

        assertThat(paymentId).isEqualTo(99);
    }

    @Test
    void listPayments_clampsLimitAndOffset() {
        OffsetDateTime from = OffsetDateTime.parse("2024-01-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2024-01-02T00:00:00Z");
        when(paymentRepository.findRecent(eq(1), eq(true), any(), any(), eq(1), eq(0)))
                .thenReturn(List.of(new Payment()));

        List<Payment> result = paymentService.listPayments(1, true, from, to, 0, -5);

        assertThat(result).hasSize(1);
        verify(paymentRepository).findRecent(1, true, from.toLocalDateTime(), to.toLocalDateTime(), 1, 0);
    }

    @Test
    void listPayments_capsLimitAtMaximum() {
        when(paymentRepository.findRecent(eq(null), eq(null), eq(null), eq(null), eq(500), eq(10)))
                .thenReturn(List.of());

        List<Payment> result = paymentService.listPayments(null, null, null, null, 1000, 10);

        assertThat(result).isEmpty();
        verify(paymentRepository).findRecent(null, null, null, null, 500, 10);
    }

    @Test
    void paymentExists_delegatesToRepository() {
        when(paymentRepository.existsByOrderId(5)).thenReturn(true);

        assertThat(paymentService.paymentExists(5)).isTrue();
    }

    @Test
    void processCashPayment_rejectsInsufficientAmount() {
        Order order = new Order();
        order.setTotalAmount(BigDecimal.valueOf(20));
        when(orderRepository.findById(7)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.processCashPayment(7, BigDecimal.valueOf(10)))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("less than order total");
    }

    @Test
    void processCashPayment_returnsChange() {
        Order order = new Order();
        order.setTotalAmount(BigDecimal.valueOf(12));
        when(orderRepository.findById(7)).thenReturn(Optional.of(order));
        when(paymentRepository.existsByOrderId(7)).thenReturn(false);
        when(paymentRepository.callProcessPayment(7, PaymentMethod.CASH.getValue()))
                .thenReturn(55);

        ChangeResponse response = new ChangeResponse();
        response.setChange(BigDecimal.valueOf(3));
        when(paymentMapper.toChangeResponse(BigDecimal.valueOf(12), BigDecimal.valueOf(15)))
                .thenReturn(response);

        ChangeResponse result = paymentService.processCashPayment(7, BigDecimal.valueOf(15));

        assertThat(result.getChange()).isEqualByComparingTo(BigDecimal.valueOf(3));
        verify(paymentMapper).toChangeResponse(BigDecimal.valueOf(12), BigDecimal.valueOf(15));
    }
}
