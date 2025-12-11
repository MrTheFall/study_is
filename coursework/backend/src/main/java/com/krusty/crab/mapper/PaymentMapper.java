package com.krusty.crab.mapper;

import com.krusty.crab.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PaymentMapper {
    
    @Mapping(target = "method", expression = "java(mapPaymentMethod(entity.getMethod()))")
    @Mapping(target = "orderId", expression = "java(entity.getOrder() != null ? entity.getOrder().getId() : null)")
    @Mapping(target = "paidAt", expression = "java(mapDateTime(entity.getPaidAt()))")
    com.krusty.crab.dto.generated.Payment toDto(com.krusty.crab.entity.Payment entity);
    
    default OffsetDateTime mapDateTime(LocalDateTime value) {
        return value != null ? value.atOffset(ZoneOffset.UTC) : null;
    }
    
    default com.krusty.crab.dto.generated.PaymentMethod mapPaymentMethod(com.krusty.crab.entity.enums.PaymentMethod value) {
        if (value == null) {
            return null;
        }
        return com.krusty.crab.dto.generated.PaymentMethod.fromValue(value.getValue());
    }
    
    default com.krusty.crab.dto.generated.ChangeResponse toChangeResponse(BigDecimal orderTotal, BigDecimal amountReceived) {
        BigDecimal change = amountReceived.subtract(orderTotal);
        com.krusty.crab.dto.generated.ChangeResponse response = new com.krusty.crab.dto.generated.ChangeResponse();
        response.setOrderTotal(orderTotal);
        response.setAmountReceived(amountReceived);
        response.setChange(change);
        return response;
    }
}

