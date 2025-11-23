package com.krusty.crab.entity.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import com.fasterxml.jackson.annotation.JsonValue;


public enum OrderStatus {
    PENDING("pending"),
    CONFIRMED("confirmed"),
    PREPARING("preparing"),
    READY("ready"),
    DELIVERING("delivering"),
    DELIVERED("delivered"),
    COMPLETED("completed"),
    CANCELLED("cancelled");
    
    private final String value;
    
    OrderStatus(String value) {
        this.value = value;
    }
    
    @JsonValue
    public String getValue() {
        return value;
    }
    
    public static OrderStatus fromValue(String value) {
        for (OrderStatus status : OrderStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown OrderStatus: " + value);
    }
    
    @Converter(autoApply = true)
    public static class OrderStatusConverter implements AttributeConverter<OrderStatus, String> {
        @Override
        public String convertToDatabaseColumn(OrderStatus attribute) {
            return attribute != null ? attribute.getValue() : null;
        }
        
        @Override
        public OrderStatus convertToEntityAttribute(String dbData) {
            return dbData != null ? OrderStatus.fromValue(dbData) : null;
        }
    }
}

