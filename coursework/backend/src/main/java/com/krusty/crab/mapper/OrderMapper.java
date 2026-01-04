package com.krusty.crab.mapper;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        uses = {CourierMapper.class})
public interface OrderMapper {

    @Mapping(target = "type", expression = "java(mapOrderType(entity.getType()))")
    @Mapping(target = "status", expression = "java(mapOrderStatus(entity.getStatus()))")
    @Mapping(target = "paymentMethod", expression = "java(mapPaymentMethod(entity.getPaymentMethod()))")
    @Mapping(target = "clientId", expression = "java(entity.getClient() != null ? entity.getClient().getId() : null)")
    @Mapping(
            target = "createdByEmployeeId",
            expression = "java(entity.getCreatedByEmployee() != null ? entity.getCreatedByEmployee().getId() : null)")
    @Mapping(
            target = "acceptedByEmployeeId",
            expression = "java(entity.getAcceptedByEmployee() != null ? entity.getAcceptedByEmployee().getId() : null)")
    @Mapping(
            target = "courierId",
            expression = "java(entity.getCourier() != null ? entity.getCourier().getId() : null)")
    @Mapping(target = "courier", source = "courier")
    @Mapping(target = "createdAt", expression = "java(mapDateTime(entity.getCreatedAt()))")
    @Mapping(target = "updatedAt", expression = "java(mapDateTime(entity.getUpdatedAt()))")
    @Mapping(target = "preparingAt", expression = "java(mapDateTime(entity.getPreparingAt()))")
    @Mapping(target = "readyAt", expression = "java(mapDateTime(entity.getReadyAt()))")
    @Mapping(target = "deliveredAt", expression = "java(mapDateTime(entity.getDeliveredAt()))")
    com.krusty.crab.dto.generated.Order toDto(com.krusty.crab.entity.Order entity);

    default OffsetDateTime mapDateTime(LocalDateTime value) {
        return value != null ? value.atOffset(ZoneOffset.UTC) : null;
    }

    default com.krusty.crab.dto.generated.OrderType mapOrderType(com.krusty.crab.entity.enums.OrderType value) {
        if (value == null) {
            return null;
        }
        return com.krusty.crab.dto.generated.OrderType.fromValue(value.getValue());
    }

    default com.krusty.crab.dto.generated.OrderStatus mapOrderStatus(com.krusty.crab.entity.enums.OrderStatus value) {
        if (value == null) {
            return null;
        }
        return com.krusty.crab.dto.generated.OrderStatus.fromValue(value.getValue());
    }

    default com.krusty.crab.dto.generated.PaymentMethod mapPaymentMethod(
            com.krusty.crab.entity.enums.PaymentMethod value) {
        if (value == null) {
            return null;
        }
        return com.krusty.crab.dto.generated.PaymentMethod.fromValue(value.getValue());
    }

    default com.krusty.crab.dto.generated.PlaceOrder201Response toPlaceOrderResponse(Integer orderId) {
        com.krusty.crab.dto.generated.PlaceOrder201Response response =
                new com.krusty.crab.dto.generated.PlaceOrder201Response();
        response.setOrderId(orderId);
        return response;
    }

    java.util.List<com.krusty.crab.dto.generated.Order> toDtoList(
            java.util.List<com.krusty.crab.entity.Order> entities);
}
