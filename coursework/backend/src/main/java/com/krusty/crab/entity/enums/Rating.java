package com.krusty.crab.entity.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import com.fasterxml.jackson.annotation.JsonValue;


public enum Rating {
    ONE(1),
    TWO(2),
    THREE(3),
    FOUR(4),
    FIVE(5);
    
    private final int value;
    
    Rating(int value) {
        this.value = value;
    }
    
    @JsonValue
    public int getValue() {
        return value;
    }
    
    public static Rating fromValue(int value) {
        for (Rating rating : Rating.values()) {
            if (rating.value == value) {
                return rating;
            }
        }
        throw new IllegalArgumentException("Rating must be between 1 and 5, got: " + value);
    }
    
    @Converter(autoApply = true)
    public static class RatingConverter implements AttributeConverter<Rating, Integer> {
        @Override
        public Integer convertToDatabaseColumn(Rating attribute) {
            return attribute != null ? attribute.getValue() : null;
        }
        
        @Override
        public Rating convertToEntityAttribute(Integer dbData) {
            return dbData != null ? Rating.fromValue(dbData) : null;
        }
    }
}

