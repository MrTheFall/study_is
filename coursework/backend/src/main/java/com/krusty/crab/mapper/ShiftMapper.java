package com.krusty.crab.mapper;

import com.krusty.crab.dto.generated.ShiftCreateRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ShiftMapper {
    
    @Mapping(target = "id", ignore = true)
    com.krusty.crab.entity.Shift toEntity(ShiftCreateRequest request);
    
    com.krusty.crab.dto.generated.Shift toDto(com.krusty.crab.entity.Shift entity);
    
    @Mapping(target = "id", ignore = true)
    void updateEntityFromRequest(ShiftCreateRequest request, @MappingTarget com.krusty.crab.entity.Shift entity);
    
    @Mapping(target = "employeeId", expression = "java(entity.getEmployee() != null ? entity.getEmployee().getId() : null)")
    @Mapping(target = "shiftId", expression = "java(entity.getShift() != null ? entity.getShift().getId() : null)")
    com.krusty.crab.dto.generated.EmployeeShift toDto(com.krusty.crab.entity.EmployeeShift entity);
}

