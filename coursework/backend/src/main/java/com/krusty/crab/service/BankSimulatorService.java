package com.krusty.crab.service;

import com.krusty.crab.dto.bank.BankPaymentInitRequest;
import com.krusty.crab.dto.bank.BankPaymentInitResponse;
import com.krusty.crab.entity.BankPaymentSession;
import com.krusty.crab.entity.enums.BankPaymentStatus;
import com.krusty.crab.exception.ValidationException;
import com.krusty.crab.repository.BankPaymentSessionRepository;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankSimulatorService {

    private final BankPaymentSessionRepository sessionRepository;
    private final BankSignatureService bankSignatureService;

    @Value("${bank.simulator.otp:123456}")
    private String fixedOtp;

    @Value("${bank.simulator.challenge-ttl-minutes:10}")
    private int challengeTtlMinutes;

    public BankPaymentInitResponse initiatePayment(
            BankPaymentInitRequest request, String signature, String timestamp, String nonce, String baseUrl) {
        validateRequest(request);

        long ts = parseTimestamp(timestamp);
        bankSignatureService.validateTimestampOrThrow(ts);

        Map<String, String> signaturePayload = Map.of(
                "merchantId", request.merchantId(),
                "orderId", request.orderId().toString(),
                "amount", request.amount().stripTrailingZeros().toPlainString(),
                "currency", request.currency(),
                "returnUrl", request.returnUrl(),
                "callbackUrl", request.callbackUrl(),
                "timestamp", String.valueOf(ts),
                "nonce", nonce);
        bankSignatureService.verifyOrThrow(signaturePayload, signature);

        String sanitizedNumber = request.cardNumber().replaceAll("\\s+", "");
        String last4 = sanitizedNumber.length() >= 4
                ? sanitizedNumber.substring(sanitizedNumber.length() - 4)
                : sanitizedNumber;

        boolean failureForced =
                Boolean.TRUE.equals(request.shouldSimulateFailure()) || "0000000000000000".equals(sanitizedNumber);

        LocalDateTime now = LocalDateTime.now();
        BankPaymentSession session = BankPaymentSession.builder()
                .id(UUID.randomUUID())
                .orderId(request.orderId())
                .amount(request.amount())
                .status(BankPaymentStatus.CHALLENGE_REQUIRED)
                .merchantId(request.merchantId())
                .cardLast4(last4)
                .otpCode(resolveOtp())
                .returnUrl(request.returnUrl())
                .callbackUrl(request.callbackUrl())
                .failureForced(failureForced)
                .createdAt(now)
                .updatedAt(now)
                .expiresAt(now.plusMinutes(challengeTtlMinutes))
                .build();

        sessionRepository.save(session);
        String acsUrl = normalizeBaseUrl(baseUrl) + "/bank/3ds/" + session.getId();
        return new BankPaymentInitResponse(
                session.getId(), acsUrl, session.getStatus().getValue());
    }

    public BankPaymentSession getSession(UUID transactionId) {
        return sessionRepository
                .findById(transactionId)
                .orElseThrow(() -> new ValidationException("Bank transaction not found"));
    }

    public boolean isExpired(BankPaymentSession session) {
        return session.getExpiresAt() != null && session.getExpiresAt().isBefore(LocalDateTime.now());
    }

    public BankPaymentSession updateStatus(BankPaymentSession session, BankPaymentStatus status, String reason) {
        session.setStatus(status);
        session.setFailureReason(reason);
        session.setUpdatedAt(LocalDateTime.now());
        return sessionRepository.save(session);
    }

    private void validateRequest(BankPaymentInitRequest request) {
        if (request == null) {
            throw new ValidationException("Bank payment request is required");
        }
        if (request.merchantId() == null || request.merchantId().isBlank()) {
            throw new ValidationException("merchantId is required");
        }
        if (request.orderId() == null) {
            throw new ValidationException("orderId is required");
        }
        if (request.amount() == null) {
            throw new ValidationException("amount is required");
        }
        if (request.currency() == null || request.currency().isBlank()) {
            throw new ValidationException("currency is required");
        }
        if (request.cardNumber() == null || request.cardNumber().isBlank()) {
            throw new ValidationException("cardNumber is required");
        }
        if (request.returnUrl() == null || request.returnUrl().isBlank()) {
            throw new ValidationException("returnUrl is required");
        }
        if (request.callbackUrl() == null || request.callbackUrl().isBlank()) {
            throw new ValidationException("callbackUrl is required");
        }
    }

    private long parseTimestamp(String timestamp) {
        if (timestamp == null) {
            throw new ValidationException("Missing timestamp");
        }
        try {
            return Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new ValidationException("Invalid timestamp");
        }
    }

    private String resolveOtp() {
        String otp = fixedOtp != null ? fixedOtp.trim() : "";
        return otp.isEmpty() ? "123456" : otp;
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://localhost:13228";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
