package com.krusty.crab.dto.bank;

import java.math.BigDecimal;

public record BankPaymentInitRequest(
        String merchantId,
        Integer orderId,
        BigDecimal amount,
        String currency,
        String cardNumber,
        String cardExpiry,
        String cardCvv,
        String cardHolder,
        String returnUrl,
        String callbackUrl,
        Boolean simulateFailure) {}
