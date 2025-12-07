package com.krusty.crab.mapper;

import com.krusty.crab.dto.generated.ClientRegistrationRequest;
import com.krusty.crab.dto.generated.ClientUpdateRequest;
import com.krusty.crab.entity.Client;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ClientMapper {
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "registeredAt", ignore = true)
    @Mapping(target = "loyaltyPoints", ignore = true)
    @Mapping(target = "orders", ignore = true)
    @Mapping(target = "reviews", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    Client toEntity(ClientRegistrationRequest request);
    
    com.krusty.crab.dto.generated.Client toDto(Client entity);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "registeredAt", ignore = true)
    @Mapping(target = "loyaltyPoints", ignore = true)
    @Mapping(target = "orders", ignore = true)
    @Mapping(target = "reviews", ignore = true)
    void updateEntityFromRequest(ClientUpdateRequest request, @MappingTarget Client entity);
    
    java.util.List<com.krusty.crab.dto.generated.Client> toDtoList(java.util.List<Client> entities);
}

