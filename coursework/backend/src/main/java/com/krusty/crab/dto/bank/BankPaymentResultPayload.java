package com.krusty.crab.dto.bank;

import java.math.BigDecimal;
import java.util.UUID;

public record BankPaymentResultPayload(
        UUID transactionId,
        Integer orderId,
        BigDecimal amount,
        String status,
        Long timestamp,
        String nonce,
        String signature) {}
