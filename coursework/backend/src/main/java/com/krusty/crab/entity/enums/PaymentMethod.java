package com.krusty.crab.entity.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import com.fasterxml.jackson.annotation.JsonValue;


public enum PaymentMethod {
    CASH("cash"),
    CARD("card"),
    ONLINE("online");
    
    private final String value;
    
    PaymentMethod(String value) {
        this.value = value;
    }
    
    @JsonValue
    public String getValue() {
        return value;
    }
    
    public static PaymentMethod fromValue(String value) {
        for (PaymentMethod method : PaymentMethod.values()) {
            if (method.value.equalsIgnoreCase(value)) {
                return method;
            }
        }
        throw new IllegalArgumentException("Unknown PaymentMethod: " + value);
    }
    
    @Converter(autoApply = true)
    public static class PaymentMethodConverter implements AttributeConverter<PaymentMethod, String> {
        @Override
        public String convertToDatabaseColumn(PaymentMethod attribute) {
            return attribute != null ? attribute.getValue() : null;
        }
        
        @Override
        public PaymentMethod convertToEntityAttribute(String dbData) {
            return dbData != null ? PaymentMethod.fromValue(dbData) : null;
        }
    }
}

