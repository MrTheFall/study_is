package com.krusty.crab.mapper;

import com.krusty.crab.dto.generated.CourierCreateRequest;
import com.krusty.crab.entity.Courier;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface CourierMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "orders", ignore = true)
    @Mapping(target = "busy", ignore = true)
    Courier toEntity(CourierCreateRequest request);

    com.krusty.crab.dto.generated.Courier toDto(Courier entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "orders", ignore = true)
    @Mapping(target = "busy", ignore = true)
    void updateEntityFromRequest(CourierCreateRequest request, @MappingTarget Courier entity);

    java.util.List<com.krusty.crab.dto.generated.Courier> toDtoList(java.util.List<Courier> entities);
}
