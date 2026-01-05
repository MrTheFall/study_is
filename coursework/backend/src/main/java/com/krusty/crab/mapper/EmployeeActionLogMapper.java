package com.krusty.crab.mapper;

import com.krusty.crab.entity.EmployeeActionLog;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface EmployeeActionLogMapper {

    @Mapping(
            target = "employeeId",
            expression = "java(entity.getEmployee() != null ? entity.getEmployee().getId() : null)")
    @Mapping(
            target = "employeeName",
            expression = "java(entity.getEmployee() != null ? entity.getEmployee().getFullName() : null)")
    @Mapping(target = "createdAt", expression = "java(mapDateTime(entity.getCreatedAt()))")
    com.krusty.crab.dto.generated.AuditLogEntry toDto(EmployeeActionLog entity);

    default OffsetDateTime mapDateTime(LocalDateTime value) {
        return value != null ? value.atOffset(ZoneOffset.UTC) : null;
    }

    java.util.List<com.krusty.crab.dto.generated.AuditLogEntry> toDtoList(java.util.List<EmployeeActionLog> entities);
}
