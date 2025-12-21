package com.krusty.crab.util;

import com.krusty.crab.dto.bank.BankPaymentResultPayload;

import java.util.Map;

public final class BankPayloadUtil {

    private BankPayloadUtil() {}

    public static Map<String, String> buildSignaturePayload(BankPaymentResultPayload payload) {
        return buildSignaturePayload(
            payload.transactionId().toString(),
            payload.orderId().toString(),
            payload.amount().stripTrailingZeros().toPlainString(),
            payload.status(),
            payload.timestamp().toString(),
            payload.nonce()
        );
    }

    public static Map<String, String> buildSignaturePayload(
        String transactionId,
        String orderId,
        String amount,
        String status,
        String timestamp,
        String nonce
    ) {
        return Map.of(
            "transactionId", transactionId,
            "orderId", orderId,
            "amount", amount,
            "status", status,
            "timestamp", timestamp,
            "nonce", nonce
        );
    }
}
