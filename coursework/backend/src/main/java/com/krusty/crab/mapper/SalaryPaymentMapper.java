package com.krusty.crab.mapper;

import com.krusty.crab.entity.SalaryPayment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface SalaryPaymentMapper {

    @Mapping(target = "employeeId", expression = "java(entity.getEmployee() != null ? entity.getEmployee().getId() : null)")
    @Mapping(target = "employeeName", expression = "java(entity.getEmployee() != null ? entity.getEmployee().getFullName() : null)")
    @Mapping(target = "paidAt", expression = "java(mapDateTime(entity.getPaidAt()))")
    com.krusty.crab.dto.generated.SalaryPayment toDto(SalaryPayment entity);

    default OffsetDateTime mapDateTime(LocalDateTime value) {
        return value != null ? value.atOffset(ZoneOffset.UTC) : null;
    }

    java.util.List<com.krusty.crab.dto.generated.SalaryPayment> toDtoList(java.util.List<SalaryPayment> entities);
}
