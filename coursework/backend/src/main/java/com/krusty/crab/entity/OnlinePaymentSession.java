package com.krusty.crab.entity;

import com.krusty.crab.entity.enums.OnlinePaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "online_payment_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OnlinePaymentSession {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_online_payment_sessions_order"))
    private Order order;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "status", nullable = false, length = 32)
    @Convert(converter = OnlinePaymentStatus.OnlinePaymentStatusConverter.class)
    private OnlinePaymentStatus status;

    @Column(name = "bank_transaction_id", columnDefinition = "uuid")
    private UUID bankTransactionId;

    @Column(name = "redirect_url")
    private String redirectUrl;

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
