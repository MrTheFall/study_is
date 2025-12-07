package com.krusty.crab.mapper;

import com.krusty.crab.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.math.BigDecimal;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PaymentMapper {
    
    @Mapping(target = "method", expression = "java(entity.getMethod() != null ? entity.getMethod().getValue() : null)")
    @Mapping(target = "orderId", expression = "java(entity.getOrder() != null ? entity.getOrder().getId() : null)")
    com.krusty.crab.dto.generated.Payment toDto(com.krusty.crab.entity.Payment entity);
    
    default com.krusty.crab.dto.generated.ChangeResponse toChangeResponse(BigDecimal orderTotal, BigDecimal amountReceived) {
        BigDecimal change = amountReceived.subtract(orderTotal);
        com.krusty.crab.dto.generated.ChangeResponse response = new com.krusty.crab.dto.generated.ChangeResponse();
        response.setOrderTotal(orderTotal);
        response.setAmountReceived(amountReceived);
        response.setChange(change);
        return response;
    }
}

