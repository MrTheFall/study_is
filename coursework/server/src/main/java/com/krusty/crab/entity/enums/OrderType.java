package com.krusty.crab.entity.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import com.fasterxml.jackson.annotation.JsonValue;


public enum OrderType {
    DINE_IN("dine_in"),
    TAKEOUT("takeout"),
    DELIVERY("delivery");
    
    private final String value;
    
    OrderType(String value) {
        this.value = value;
    }
    
    @JsonValue
    public String getValue() {
        return value;
    }
    
    public static OrderType fromValue(String value) {
        for (OrderType type : OrderType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown OrderType: " + value);
    }
    
    @Converter(autoApply = true)
    public static class OrderTypeConverter implements AttributeConverter<OrderType, String> {
        @Override
        public String convertToDatabaseColumn(OrderType attribute) {
            return attribute != null ? attribute.getValue() : null;
        }
        
        @Override
        public OrderType convertToEntityAttribute(String dbData) {
            return dbData != null ? OrderType.fromValue(dbData) : null;
        }
    }
}

