package com.krusty.crab.service;

import com.krusty.crab.dto.bank.BankPaymentInitRequest;
import com.krusty.crab.dto.bank.BankPaymentInitResponse;
import com.krusty.crab.dto.bank.BankPaymentResultPayload;
import com.krusty.crab.entity.OnlinePaymentSession;
import com.krusty.crab.entity.Order;
import com.krusty.crab.entity.enums.BankPaymentStatus;
import com.krusty.crab.entity.enums.OnlinePaymentStatus;
import com.krusty.crab.entity.enums.PaymentMethod;
import com.krusty.crab.exception.PaymentException;
import com.krusty.crab.exception.ValidationException;
import com.krusty.crab.repository.OnlinePaymentSessionRepository;
import com.krusty.crab.repository.PaymentRepository;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OnlinePaymentService {

    private static final EnumSet<OnlinePaymentStatus> ACTIVE_STATUSES =
            EnumSet.of(OnlinePaymentStatus.CREATED, OnlinePaymentStatus.CHALLENGE_REQUIRED);

    private final OnlinePaymentSessionRepository sessionRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final BankSimulatorClient bankSimulatorClient;
    private final BankSignatureService bankSignatureService;

    @Value("${bank.simulator.merchant-id:krusty-crab-demo}")
    private String merchantId;

    @Value("${bank.simulator.challenge-ttl-minutes:10}")
    private int challengeTtlMinutes;

    public OnlinePaymentSession startPayment(
            Order order,
            CardData cardData,
            boolean simulateFailure,
            String returnUrl,
            String callbackUrl,
            String bankBaseUrl) {
        if (order.getId() == null) {
            throw new ValidationException("orderId is required");
        }
        if (paymentRepository.existsByOrderId(order.getId())) {
            throw new PaymentException("Payment already exists for order " + order.getId());
        }

        validateCardData(cardData);

        OnlinePaymentSession existing = sessionRepository
                .findFirstByOrderIdAndStatusInOrderByCreatedAtDesc(order.getId(), ACTIVE_STATUSES)
                .filter(session -> !isExpired(session))
                .orElse(null);

        if (existing != null) {
            return existing;
        }

        String sanitizedNumber = sanitizeCardNumber(cardData.number());
        String last4 = sanitizedNumber.length() >= 4
                ? sanitizedNumber.substring(sanitizedNumber.length() - 4)
                : sanitizedNumber;

        BankPaymentInitRequest bankRequest = new BankPaymentInitRequest(
                merchantId,
                order.getId(),
                order.getTotalAmount(),
                "USD",
                sanitizedNumber,
                cardData.expiry(),
                cardData.cvv(),
                cardData.holder(),
                returnUrl,
                callbackUrl,
                simulateFailure);

        BankPaymentInitResponse bankResponse = bankSimulatorClient.initiatePayment(bankRequest, bankBaseUrl);

        LocalDateTime now = LocalDateTime.now();
        OnlinePaymentSession session = OnlinePaymentSession.builder()
                .id(UUID.randomUUID())
                .order(order)
                .amount(order.getTotalAmount())
                .status(OnlinePaymentStatus.CHALLENGE_REQUIRED)
                .bankTransactionId(bankResponse.transactionId())
                .redirectUrl(bankResponse.acsUrl())
                .cardLast4(last4)
                .createdAt(now)
                .updatedAt(now)
                .expiresAt(now.plusMinutes(challengeTtlMinutes))
                .build();

        sessionRepository.save(session);
        log.info(
                "Online payment session created for order {} with bank tx {}",
                order.getId(),
                bankResponse.transactionId());
        return session;
    }

    @Transactional
    public OnlinePaymentStatus finalizeFromBank(BankPaymentResultPayload payload) {
        validateBankPayload(payload);

        OnlinePaymentSession session = sessionRepository
                .findByBankTransactionId(payload.transactionId())
                .orElseThrow(() -> new PaymentException("Unknown bank transaction " + payload.transactionId()));

        if (session.getStatus() == OnlinePaymentStatus.SUCCEEDED
                || session.getStatus() == OnlinePaymentStatus.CANCELLED
                || session.getStatus() == OnlinePaymentStatus.FAILED) {
            return session.getStatus();
        }

        if (payload.orderId() == null
                || !payload.orderId().equals(session.getOrder().getId())) {
            throw new ValidationException("Order does not match payment session");
        }
        if (payload.amount() == null
                || session.getAmount() == null
                || payload.amount().compareTo(session.getAmount()) != 0) {
            throw new ValidationException("Amount does not match payment session");
        }

        BankPaymentStatus bankStatus = BankPaymentStatus.fromValue(payload.status());
        OnlinePaymentStatus finalStatus = mapToOnlineStatus(bankStatus);

        if (finalStatus == OnlinePaymentStatus.SUCCEEDED) {
            try {
                paymentService.processPayment(session.getOrder().getId(), PaymentMethod.ONLINE, false);
            } catch (PaymentException e) {
                if (!paymentRepository.existsByOrderId(session.getOrder().getId())) {
                    session.setFailureReason(e.getMessage());
                    finalStatus = OnlinePaymentStatus.FAILED;
                }
            }
        } else if (finalStatus == OnlinePaymentStatus.FAILED || finalStatus == OnlinePaymentStatus.CANCELLED) {
            session.setFailureReason(bankStatus.getValue());
        }

        session.setStatus(finalStatus);
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);
        return finalStatus;
    }

    private void validateBankPayload(BankPaymentResultPayload payload) {
        if (payload == null) {
            throw new ValidationException("Payment result payload is required");
        }
        if (payload.transactionId() == null) {
            throw new ValidationException("Missing transactionId");
        }
        if (payload.orderId() == null) {
            throw new ValidationException("Missing orderId");
        }
        if (payload.amount() == null) {
            throw new ValidationException("Missing amount");
        }
        if (payload.status() == null || payload.status().isBlank()) {
            throw new ValidationException("Missing status");
        }
        if (payload.timestamp() == null) {
            throw new ValidationException("Missing timestamp");
        }
        if (payload.nonce() == null || payload.nonce().isBlank()) {
            throw new ValidationException("Missing nonce");
        }
        if (payload.signature() == null || payload.signature().isBlank()) {
            throw new ValidationException("Missing signature");
        }
        bankSignatureService.validateTimestampOrThrow(payload.timestamp());

        var signaturePayload = com.krusty.crab.util.BankPayloadUtil.buildSignaturePayload(payload);
        bankSignatureService.verifyOrThrow(signaturePayload, payload.signature());
    }

    private boolean isExpired(OnlinePaymentSession session) {
        return session.getExpiresAt() != null && session.getExpiresAt().isBefore(LocalDateTime.now());
    }

    private void validateCardData(CardData cardData) {
        if (cardData == null) {
            throw new ValidationException("Card data is required");
        }
        String number = sanitizeCardNumber(cardData.number());
        if (!number.matches("\\d{16}")) {
            throw new ValidationException("Invalid card number");
        }
        if (cardData.expiry() == null || !cardData.expiry().matches("^(0[1-9]|1[0-2])\\/\\d{2}$")) {
            throw new ValidationException("Invalid card expiry");
        }
        if (cardData.cvv() == null || !cardData.cvv().matches("\\d{3,4}")) {
            throw new ValidationException("Invalid card CVV");
        }
    }

    private String sanitizeCardNumber(String number) {
        return number == null ? "" : number.replaceAll("\\s+", "");
    }

    private OnlinePaymentStatus mapToOnlineStatus(BankPaymentStatus bankStatus) {
        return switch (bankStatus) {
            case APPROVED -> OnlinePaymentStatus.SUCCEEDED;
            case CANCELLED -> OnlinePaymentStatus.CANCELLED;
            case DECLINED, FAILED -> OnlinePaymentStatus.FAILED;
            case CHALLENGE_REQUIRED, CREATED -> OnlinePaymentStatus.CHALLENGE_REQUIRED;
        };
    }

    public record CardData(String number, String expiry, String cvv, String holder) {}
}
