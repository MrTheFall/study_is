package com.krusty.crab.service;

import com.krusty.crab.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BankSignatureService {

    private static final Duration ALLOWED_SKEW = Duration.ofMinutes(10);

    private final byte[] secret;

    public BankSignatureService(@Value("${bank.simulator.secret:bank-simulator-secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String sign(Map<String, String> payload) {
        String data = canonicalize(payload);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign payload", e);
        }
    }

    public void verifyOrThrow(Map<String, String> payload, String signature) {
        if (signature == null || signature.isBlank()) {
            throw new ValidationException("Missing signature");
        }
        String expected = sign(payload);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new ValidationException("Invalid signature");
        }
    }

    public void validateTimestampOrThrow(long timestampSeconds) {
        Instant now = Instant.now();
        Instant ts = Instant.ofEpochSecond(timestampSeconds);
        if (ts.isBefore(now.minus(ALLOWED_SKEW)) || ts.isAfter(now.plus(ALLOWED_SKEW))) {
            log.warn("Bank signature timestamp is out of range: {}", timestampSeconds);
            throw new ValidationException("Signature timestamp is out of range");
        }
    }

    public String canonicalize(Map<String, String> payload) {
        Map<String, String> sorted = new TreeMap<>(payload);
        return sorted.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining("&"));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
