package com.krusty.crab.service;

import com.krusty.crab.dto.bank.BankPaymentInitRequest;
import com.krusty.crab.dto.bank.BankPaymentInitResponse;
import com.krusty.crab.exception.PaymentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class BankSimulatorClient {

    private final RestTemplate restTemplate;
    private final BankSignatureService bankSignatureService;
    private final BankSimulatorService bankSimulatorService;

    @Value("${bank.simulator.base-url:http://localhost:13228}")
    private String baseUrl;

    @Value("${bank.simulator.mode:http}")
    private String mode;

    public BankPaymentInitResponse initiatePayment(BankPaymentInitRequest request, String publicBaseUrl) {
        long timestamp = Instant.now().getEpochSecond();
        String nonce = UUID.randomUUID().toString().replace("-", "");

        Map<String, String> signaturePayload = Map.of(
            "merchantId", request.merchantId(),
            "orderId", request.orderId().toString(),
            "amount", request.amount().stripTrailingZeros().toPlainString(),
            "currency", request.currency(),
            "returnUrl", request.returnUrl(),
            "callbackUrl", request.callbackUrl(),
            "timestamp", String.valueOf(timestamp),
            "nonce", nonce
        );
        String signature = bankSignatureService.sign(signaturePayload);

        if ("internal".equalsIgnoreCase(mode)) {
            return bankSimulatorService.initiatePayment(
                request,
                signature,
                String.valueOf(timestamp),
                nonce,
                publicBaseUrl != null ? publicBaseUrl : baseUrl
            );
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Signature", signature);
        headers.add("X-Timestamp", String.valueOf(timestamp));
        headers.add("X-Nonce", nonce);
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            headers.add("X-External-Base-Url", publicBaseUrl);
        }

        HttpEntity<BankPaymentInitRequest> entity = new HttpEntity<>(request, headers);
        String url = baseUrl + "/bank/api/payments";
        BankPaymentInitResponse response = restTemplate.postForObject(url, entity, BankPaymentInitResponse.class);
        if (response == null) {
            throw new PaymentException("Bank simulator did not return a response");
        }
        return response;
    }
}
