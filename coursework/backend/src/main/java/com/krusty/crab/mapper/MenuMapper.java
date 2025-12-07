package com.krusty.crab.mapper;

import com.krusty.crab.dto.generated.MenuItemCreateRequest;
import com.krusty.crab.entity.MenuItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface MenuMapper {
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "recipe", ignore = true)
    @Mapping(target = "orderItems", ignore = true)
    MenuItem toEntity(MenuItemCreateRequest request);
    
    com.krusty.crab.dto.generated.MenuItem toDto(MenuItem entity);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "recipe", ignore = true)
    @Mapping(target = "orderItems", ignore = true)
    void updateEntityFromRequest(MenuItemCreateRequest request, @MappingTarget MenuItem entity);
}

