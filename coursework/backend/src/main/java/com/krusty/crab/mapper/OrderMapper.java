package com.krusty.crab.mapper;

import com.krusty.crab.dto.generated.Order;
import com.krusty.crab.dto.generated.PlaceOrder201Response;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        uses = {CourierMapper.class})
public interface OrderMapper {

    @Mapping(target = "clientId", source = "client.id")
    @Mapping(target = "createdByEmployeeId", source = "createdByEmployee.id")
    @Mapping(target = "acceptedByEmployeeId", source = "acceptedByEmployee.id")
    @Mapping(target = "courierId", source = "courier.id")
    Order toDto(com.krusty.crab.entity.Order entity);

    default OffsetDateTime mapDateTime(LocalDateTime value) {
        return value != null ? value.atOffset(ZoneOffset.UTC) : null;
    }

    default PlaceOrder201Response toPlaceOrderResponse(Integer orderId) {
        PlaceOrder201Response response = new PlaceOrder201Response();
        response.setOrderId(orderId);
        return response;
    }

    List<Order> toDtoList(List<com.krusty.crab.entity.Order> entities);
}
