package com.krusty.crab.mapper;

import com.krusty.crab.dto.generated.ReviewCreateRequest;
import com.krusty.crab.entity.Review;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ReviewMapper {
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "order", ignore = true)
    @Mapping(target = "client", ignore = true)
    @Mapping(target = "rating", expression = "java(request.getRating() != null ? com.krusty.crab.entity.enums.Rating.fromValue(request.getRating()) : null)")
    com.krusty.crab.entity.Review toEntity(ReviewCreateRequest request);
    
    @Mapping(target = "orderId", expression = "java(entity.getOrder() != null ? entity.getOrder().getId() : null)")
    @Mapping(target = "clientId", expression = "java(entity.getClient() != null ? entity.getClient().getId() : null)")
    @Mapping(target = "rating", expression = "java(entity.getRating() != null ? entity.getRating().getValue() : null)")
    @Mapping(target = "createdAt", expression = "java(mapDateTime(entity.getCreatedAt()))")
    com.krusty.crab.dto.generated.Review toDto(com.krusty.crab.entity.Review entity);
    
    default OffsetDateTime mapDateTime(LocalDateTime value) {
        return value != null ? value.atOffset(ZoneOffset.UTC) : null;
    }
    
    default com.krusty.crab.entity.Review toEntityWithRelations(
            ReviewCreateRequest request, 
            com.krusty.crab.entity.Order order, 
            com.krusty.crab.entity.Client client) {
        com.krusty.crab.entity.Review review = toEntity(request);
        review.setOrder(order);
        review.setClient(client);
        return review;
    }
    
    java.util.List<com.krusty.crab.dto.generated.Review> toDtoList(java.util.List<com.krusty.crab.entity.Review> entities);
}

