package com.krusty.crab.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new StringToOrderStatusConverter());
        registry.addConverter(new StringToOrderTypeConverter());
        registry.addConverter(new StringToPaymentMethodConverter());
    }
    
    // Конвертер для OrderStatus
    public static class StringToOrderStatusConverter implements Converter<String, com.krusty.crab.dto.generated.OrderStatus> {
        @Override
        public com.krusty.crab.dto.generated.OrderStatus convert(String source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            // Проверяем, что значение не содержит знак равенства (это может быть ошибка в URL)
            if (source.contains("=")) {
                throw new IllegalArgumentException(
                    String.format("Invalid OrderStatus value: '%s'. Did you mean to use query parameter 'clientId' instead of 'status'? Valid status values: pending, confirmed, preparing, ready, delivering, delivered, completed, cancelled", 
                    source));
            }
            try {
                return com.krusty.crab.dto.generated.OrderStatus.fromValue(source);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                    String.format("Invalid OrderStatus value: '%s'. Valid values: pending, confirmed, preparing, ready, delivering, delivered, completed, cancelled", 
                    source), e);
            }
        }
    }
    
    // Конвертер для OrderType
    public static class StringToOrderTypeConverter implements Converter<String, com.krusty.crab.dto.generated.OrderType> {
        @Override
        public com.krusty.crab.dto.generated.OrderType convert(String source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            if (source.contains("=")) {
                throw new IllegalArgumentException(
                    String.format("Invalid OrderType value: '%s'. Valid values: dine_in, takeout, delivery", source));
            }
            try {
                return com.krusty.crab.dto.generated.OrderType.fromValue(source);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                    String.format("Invalid OrderType value: '%s'. Valid values: dine_in, takeout, delivery", source), e);
            }
        }
    }
    
    // Конвертер для PaymentMethod
    public static class StringToPaymentMethodConverter implements Converter<String, com.krusty.crab.dto.generated.PaymentMethod> {
        @Override
        public com.krusty.crab.dto.generated.PaymentMethod convert(String source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            if (source.contains("=")) {
                throw new IllegalArgumentException(
                    String.format("Invalid PaymentMethod value: '%s'. Valid values: cash, card, online", source));
            }
            try {
                return com.krusty.crab.dto.generated.PaymentMethod.fromValue(source);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                    String.format("Invalid PaymentMethod value: '%s'. Valid values: cash, card, online", source), e);
            }
        }
    }
}

