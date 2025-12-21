package com.krusty.crab.dto.bank;

import java.util.UUID;

public record BankPaymentInitResponse(
    UUID transactionId,
    String acsUrl,
    String status
) {}
