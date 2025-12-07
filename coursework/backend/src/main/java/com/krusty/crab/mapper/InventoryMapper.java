package com.krusty.crab.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface InventoryMapper {
    
    @Mapping(target = "ingredientId", expression = "java(entity.getIngredient() != null ? entity.getIngredient().getId() : null)")
    com.krusty.crab.dto.generated.InventoryRecord toDto(com.krusty.crab.entity.InventoryRecord entity);
    

}

