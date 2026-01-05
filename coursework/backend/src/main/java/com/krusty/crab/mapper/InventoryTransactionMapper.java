package com.krusty.crab.mapper;

import com.krusty.crab.entity.InventoryTransaction;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface InventoryTransactionMapper {

    @Mapping(
            target = "ingredientId",
            expression = "java(entity.getIngredient() != null ? entity.getIngredient().getId() : null)")
    @Mapping(
            target = "ingredientName",
            expression = "java(entity.getIngredient() != null ? entity.getIngredient().getName() : null)")
    @Mapping(
            target = "employeeId",
            expression = "java(entity.getEmployee() != null ? entity.getEmployee().getId() : null)")
    @Mapping(
            target = "employeeName",
            expression = "java(entity.getEmployee() != null ? entity.getEmployee().getFullName() : null)")
    @Mapping(target = "orderId", expression = "java(entity.getOrder() != null ? entity.getOrder().getId() : null)")
    @Mapping(target = "createdAt", expression = "java(mapDateTime(entity.getCreatedAt()))")
    com.krusty.crab.dto.generated.InventoryTransaction toDto(InventoryTransaction entity);

    default OffsetDateTime mapDateTime(LocalDateTime value) {
        return value != null ? value.atOffset(ZoneOffset.UTC) : null;
    }

    java.util.List<com.krusty.crab.dto.generated.InventoryTransaction> toDtoList(
            java.util.List<InventoryTransaction> entities);
}
