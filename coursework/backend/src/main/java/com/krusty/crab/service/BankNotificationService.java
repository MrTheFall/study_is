package com.krusty.crab.service;

import com.krusty.crab.dto.bank.BankPaymentResultPayload;
import com.krusty.crab.entity.BankPaymentSession;
import com.krusty.crab.entity.enums.BankPaymentStatus;
import com.krusty.crab.util.BankPayloadUtil;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankNotificationService {

    private final RestTemplate restTemplate;
    private final BankSignatureService bankSignatureService;

    @Value("${bank.simulator.notify.enabled:true}")
    private boolean notifyEnabled;

    public void notifyMerchant(BankPaymentSession session, BankPaymentStatus status) {
        if (!notifyEnabled) {
            return;
        }
        if (session.getCallbackUrl() == null || session.getCallbackUrl().isBlank()) {
            return;
        }

        long timestamp = Instant.now().getEpochSecond();
        String nonce = UUID.randomUUID().toString().replace("-", "");

        BankPaymentResultPayload payload = new BankPaymentResultPayload(
                session.getId(), session.getOrderId(), session.getAmount(), status.getValue(), timestamp, nonce, null);

        String signature = bankSignatureService.sign(BankPayloadUtil.buildSignaturePayload(payload));
        BankPaymentResultPayload signed = new BankPaymentResultPayload(
                payload.transactionId(),
                payload.orderId(),
                payload.amount(),
                payload.status(),
                payload.timestamp(),
                payload.nonce(),
                signature);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<BankPaymentResultPayload> entity = new HttpEntity<>(signed, headers);
        try {
            restTemplate.postForEntity(session.getCallbackUrl(), entity, Void.class);
        } catch (Exception e) {
            log.warn("Failed to send bank notification to {}: {}", session.getCallbackUrl(), e.getMessage());
        }
    }
}
