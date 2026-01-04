package com.krusty.crab.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface OrderItemMapper {

    @Mapping(target = "orderId", expression = "java(entity.getOrder() != null ? entity.getOrder().getId() : null)")
    @Mapping(
            target = "menuItemId",
            expression = "java(entity.getMenuItem() != null ? entity.getMenuItem().getId() : null)")
    @Mapping(target = "name", expression = "java(entity.getMenuItem() != null ? entity.getMenuItem().getName() : null)")
    com.krusty.crab.dto.generated.OrderItem toDto(com.krusty.crab.entity.OrderItem entity);

    java.util.List<com.krusty.crab.dto.generated.OrderItem> toDtoList(
            java.util.List<com.krusty.crab.entity.OrderItem> entities);
}
