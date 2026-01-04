package com.krusty.crab.config;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final String ORDER_STATUS_VALUES = allowedValues(
            com.krusty.crab.dto.generated.OrderStatus.values(),
            com.krusty.crab.dto.generated.OrderStatus::getValue);
    private static final String ORDER_TYPE_VALUES = allowedValues(
            com.krusty.crab.dto.generated.OrderType.values(),
            com.krusty.crab.dto.generated.OrderType::getValue);
    private static final String PAYMENT_METHOD_VALUES = allowedValues(
            com.krusty.crab.dto.generated.PaymentMethod.values(),
            com.krusty.crab.dto.generated.PaymentMethod::getValue);

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new StringToOrderStatusConverter());
        registry.addConverter(new StringToOrderTypeConverter());
        registry.addConverter(new StringToPaymentMethodConverter());
    }

    public static class StringToOrderStatusConverter
            implements Converter<String, com.krusty.crab.dto.generated.OrderStatus> {
        @Override
        public com.krusty.crab.dto.generated.OrderStatus convert(String source) {
            return convertEnum(
                    source,
                    com.krusty.crab.dto.generated.OrderStatus::fromValue,
                    "OrderStatus",
                    ORDER_STATUS_VALUES);
        }
    }

    public static class StringToOrderTypeConverter
            implements Converter<String, com.krusty.crab.dto.generated.OrderType> {
        @Override
        public com.krusty.crab.dto.generated.OrderType convert(String source) {
            return convertEnum(
                    source,
                    com.krusty.crab.dto.generated.OrderType::fromValue,
                    "OrderType",
                    ORDER_TYPE_VALUES);
        }
    }

    public static class StringToPaymentMethodConverter
            implements Converter<String, com.krusty.crab.dto.generated.PaymentMethod> {
        @Override
        public com.krusty.crab.dto.generated.PaymentMethod convert(String source) {
            return convertEnum(
                    source,
                    com.krusty.crab.dto.generated.PaymentMethod::fromValue,
                    "PaymentMethod",
                    PAYMENT_METHOD_VALUES);
        }
    }

    private static <T> T convertEnum(
            String source, Function<String, T> parser, String typeName, String allowedValues) {
        String normalized = source != null ? source.trim() : null;
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        try {
            return parser.apply(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    String.format("Invalid %s value: '%s'. Valid values: %s", typeName, normalized, allowedValues),
                    ex);
        }
    }

    private static <E extends Enum<E>> String allowedValues(E[] values, Function<E, String> valueMapper) {
        return Arrays.stream(values).map(valueMapper).collect(Collectors.joining(", "));
    }
}
