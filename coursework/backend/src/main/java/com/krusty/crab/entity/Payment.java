package com.krusty.crab.entity;

import com.krusty.crab.entity.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments",
    uniqueConstraints = @UniqueConstraint(name = "uq_payments_order", columnNames = "order_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true,
        foreignKey = @ForeignKey(name = "fk_payments_order"))
    private Order order;
    
    @Column(name = "method", nullable = false, length = 32)
    @Convert(converter = PaymentMethod.PaymentMethodConverter.class)
    private PaymentMethod method;
    
    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "paid_at")
    private LocalDateTime paidAt;
    
    @Column(name = "success", nullable = false)
    @Builder.Default
    private Boolean success = false;
}

