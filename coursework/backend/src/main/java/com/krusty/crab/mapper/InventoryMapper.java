package com.krusty.crab.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface InventoryMapper {
    
    @Mapping(target = "ingredientId", expression = "java(entity.getIngredient() != null ? entity.getIngredient().getId() : null)")
    @Mapping(target = "lastUpdated", expression = "java(mapDateTime(entity.getLastUpdated()))")
    com.krusty.crab.dto.generated.InventoryRecord toDto(com.krusty.crab.entity.InventoryRecord entity);

    default OffsetDateTime mapDateTime(LocalDateTime value) {
        return value != null ? value.atOffset(ZoneOffset.UTC) : null;
    }

    java.util.List<com.krusty.crab.dto.generated.InventoryRecord> toDtoList(java.util.List<com.krusty.crab.entity.InventoryRecord> entities);
}

