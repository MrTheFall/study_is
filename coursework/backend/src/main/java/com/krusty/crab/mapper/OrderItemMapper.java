package com.krusty.crab.mapper;

import com.krusty.crab.dto.generated.OrderItem;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface OrderItemMapper {

    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "menuItemId", source = "menuItem.id")
    @Mapping(target = "name", source = "menuItem.name")
    OrderItem toDto(com.krusty.crab.entity.OrderItem entity);

    List<OrderItem> toDtoList(List<com.krusty.crab.entity.OrderItem> entities);
}
