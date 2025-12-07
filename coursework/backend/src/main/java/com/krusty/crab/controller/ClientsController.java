package com.krusty.crab.controller;

import com.krusty.crab.api.ClientsApi;
import com.krusty.crab.dto.generated.ClientRegistrationRequest;
import com.krusty.crab.dto.generated.ClientUpdateRequest;
import com.krusty.crab.entity.Client;
import com.krusty.crab.mapper.ClientMapper;
import com.krusty.crab.mapper.OrderMapper;
import com.krusty.crab.service.ClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ClientsController implements ClientsApi {
    
    private final ClientService clientService;
    private final ClientMapper clientMapper;
    private final OrderMapper orderMapper;
    
    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.Client> registerClient(ClientRegistrationRequest clientRegistrationRequest) {
        log.info("Registering new client with email: {}", clientRegistrationRequest.getEmail());
        Client clientEntity = clientMapper.toEntity(clientRegistrationRequest);
        Client saved = clientService.createClient(clientEntity, clientRegistrationRequest.getPassword());
        com.krusty.crab.dto.generated.Client dto = clientMapper.toDto(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
    
    @Override
    public ResponseEntity<List<com.krusty.crab.dto.generated.Client>> getAllClients() {
        log.info("Getting all clients");
        List<Client> clients = clientService.getAllClients();
        return ResponseEntity.ok(clientMapper.toDtoList(clients));
    }
    
    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.Client> getClientById(Integer clientId) {
        log.info("Getting client by ID: {}", clientId);
        Client client = clientService.getClientById(clientId);
        com.krusty.crab.dto.generated.Client dto = clientMapper.toDto(client);
        return ResponseEntity.ok(dto);
    }
    
    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.Client> updateClient(Integer clientId, ClientUpdateRequest clientUpdateRequest) {
        log.info("Updating client with ID: {}", clientId);
        Client clientEntity = clientService.getClientById(clientId);
        clientMapper.updateEntityFromRequest(clientUpdateRequest, clientEntity);
        Client updated = clientService.updateClient(clientId, clientEntity);
        com.krusty.crab.dto.generated.Client dto = clientMapper.toDto(updated);
        return ResponseEntity.ok(dto);
    }
    
    @Override
    public ResponseEntity<List<com.krusty.crab.dto.generated.Order>> getClientOrders(Integer clientId) {
        log.info("Getting orders for client ID: {}", clientId);
        List<com.krusty.crab.entity.Order> orders = clientService.getClientOrders(clientId);
        return ResponseEntity.ok(orderMapper.toDtoList(orders));
    }
}

