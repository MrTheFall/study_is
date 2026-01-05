package com.krusty.crab.entity.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

public enum OnlinePaymentStatus {
    CREATED("created"),
    CHALLENGE_REQUIRED("challenge_required"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    CANCELLED("cancelled");

    private final String value;

    OnlinePaymentStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static OnlinePaymentStatus fromValue(String value) {
        for (OnlinePaymentStatus status : OnlinePaymentStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown OnlinePaymentStatus: " + value);
    }

    @Converter(autoApply = true)
    public static class OnlinePaymentStatusConverter implements AttributeConverter<OnlinePaymentStatus, String> {
        @Override
        public String convertToDatabaseColumn(OnlinePaymentStatus attribute) {
            return attribute != null ? attribute.getValue() : null;
        }

        @Override
        public OnlinePaymentStatus convertToEntityAttribute(String dbData) {
            return dbData != null ? OnlinePaymentStatus.fromValue(dbData) : null;
        }
    }
}
