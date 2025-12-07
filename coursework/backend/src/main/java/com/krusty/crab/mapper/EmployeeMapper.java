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
    
    default com.krusty.crab.entity.Employee toEntityWithPassword(EmployeeCreateRequest request, String password) {
        com.krusty.crab.entity.Employee employee = toEntity(request);
        if (password != null && !password.isEmpty()) {
            employee.setPasswordHash(com.krusty.crab.util.PasswordUtil.encode(password));
        }
        return employee;
    }
    
    default void updateEntityWithPassword(EmployeeCreateRequest request, @MappingTarget com.krusty.crab.entity.Employee entity) {
        updateEntityFromRequest(request, entity);
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            entity.setPasswordHash(com.krusty.crab.util.PasswordUtil.encode(request.getPassword()));
        }
    }
    
    java.util.List<com.krusty.crab.dto.generated.Employee> toDtoList(java.util.List<com.krusty.crab.entity.Employee> entities);
}

