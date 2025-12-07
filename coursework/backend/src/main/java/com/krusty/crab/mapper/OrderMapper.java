package com.krusty.crab.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface OrderMapper {
    
    @Mapping(target = "type", expression = "java(entity.getType() != null ? entity.getType().getValue() : null)")
    @Mapping(target = "status", expression = "java(entity.getStatus() != null ? entity.getStatus().getValue() : null)")
    @Mapping(target = "clientId", expression = "java(entity.getClient() != null ? entity.getClient().getId() : null)")
    @Mapping(target = "courierId", expression = "java(entity.getCourier() != null ? entity.getCourier().getId() : null)")
    com.krusty.crab.dto.generated.Order toDto(com.krusty.crab.entity.Order entity);
    
    default com.krusty.crab.dto.generated.PlaceOrder201Response toPlaceOrderResponse(Integer orderId) {
        com.krusty.crab.dto.generated.PlaceOrder201Response response = new com.krusty.crab.dto.generated.PlaceOrder201Response();
        response.setOrderId(orderId);
        return response;
    }
    
    java.util.List<com.krusty.crab.dto.generated.Order> toDtoList(java.util.List<com.krusty.crab.entity.Order> entities);
}

