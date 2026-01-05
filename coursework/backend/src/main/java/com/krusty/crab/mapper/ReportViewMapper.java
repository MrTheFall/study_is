package com.krusty.crab.mapper;

import com.krusty.crab.entity.ReportView;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ReportViewMapper {

    @Mapping(
            target = "employeeId",
            expression = "java(entity.getEmployee() != null ? entity.getEmployee().getId() : null)")
    @Mapping(
            target = "employeeName",
            expression = "java(entity.getEmployee() != null ? entity.getEmployee().getFullName() : null)")
    @Mapping(target = "from", expression = "java(mapDateTime(entity.getFromTs()))")
    @Mapping(target = "to", expression = "java(mapDateTime(entity.getToTs()))")
    @Mapping(target = "viewedAt", expression = "java(mapDateTime(entity.getViewedAt()))")
    com.krusty.crab.dto.generated.ReportView toDto(ReportView entity);

    default OffsetDateTime mapDateTime(LocalDateTime value) {
        return value != null ? value.atOffset(ZoneOffset.UTC) : null;
    }

    java.util.List<com.krusty.crab.dto.generated.ReportView> toDtoList(java.util.List<ReportView> entities);
}
