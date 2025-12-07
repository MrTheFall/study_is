package com.krusty.crab.mapper;

import com.krusty.crab.dto.generated.EmployeeCreateRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface EmployeeMapper {
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "hiredAt", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "employeeShifts", ignore = true)
    com.krusty.crab.entity.Employee toEntity(EmployeeCreateRequest request);
    
    @Mapping(target = "roleId", expression = "java(entity.getRole() != null ? entity.getRole().getId() : null)")
    com.krusty.crab.dto.generated.Employee toDto(com.krusty.crab.entity.Employee entity);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "hiredAt", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "employeeShifts", ignore = true)
    void updateEntityFromRequest(EmployeeCreateRequest request, @MappingTarget com.krusty.crab.entity.Employee entity);
}

