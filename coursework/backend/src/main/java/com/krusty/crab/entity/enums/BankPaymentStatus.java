package com.krusty.crab.entity.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

public enum BankPaymentStatus {
    CREATED("created"),
    CHALLENGE_REQUIRED("challenge_required"),
    APPROVED("approved"),
    DECLINED("declined"),
    FAILED("failed"),
    CANCELLED("cancelled");

    private final String value;

    private BankPaymentStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static BankPaymentStatus fromValue(String value) {
        for (BankPaymentStatus status : BankPaymentStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown BankPaymentStatus: " + value);
    }

    @Converter(autoApply = true)
    public static class BankPaymentStatusConverter implements AttributeConverter<BankPaymentStatus, String> {
        @Override
        public String convertToDatabaseColumn(BankPaymentStatus attribute) {
            return attribute != null ? attribute.getValue() : null;
        }

        @Override
        public BankPaymentStatus convertToEntityAttribute(String dbData) {
            return dbData != null ? BankPaymentStatus.fromValue(dbData) : null;
        }
    }
}
