package com.krusty.crab.entity;

import com.krusty.crab.entity.enums.BankPaymentStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "bank_payment_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankPaymentSession {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private Integer orderId;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "status", nullable = false, length = 32)
    @Convert(converter = BankPaymentStatus.BankPaymentStatusConverter.class)
    private BankPaymentStatus status;

    @Column(name = "merchant_id", nullable = false, length = 64)
    private String merchantId;

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "otp_code", length = 16)
    private String otpCode;

    @Column(name = "return_url")
    private String returnUrl;

    @Column(name = "callback_url")
    private String callbackUrl;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "force_failure", nullable = false)
    private Boolean forceFailure;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
