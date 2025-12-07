package com.krusty.crab.service;

import com.krusty.crab.entity.Client;
import com.krusty.crab.entity.Order;
import com.krusty.crab.exception.DuplicateEntityException;
import com.krusty.crab.exception.EntityNotFoundException;
import com.krusty.crab.repository.ClientRepository;
import com.krusty.crab.repository.OrderRepository;
import com.krusty.crab.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientService {
    
    private final ClientRepository clientRepository;
    private final OrderRepository orderRepository;
    
    public Client getClientById(Integer id) {
        return clientRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Client", id));
    }
    
    public Client getClientByEmail(String email) {
        return clientRepository.findByEmail(email)
            .orElseThrow(() -> new EntityNotFoundException("Client", "email", email));
    }
    
    public List<Client> getAllClients() {
        return clientRepository.findAll();
    }
    
    @Transactional
    public Client createClient(Client client) {
        if (clientRepository.existsByEmail(client.getEmail())) {
            throw new DuplicateEntityException("Client", "email", client.getEmail());
        }
        if (clientRepository.existsByPhone(client.getPhone())) {
            throw new DuplicateEntityException("Client", "phone", client.getPhone());
        }
        Client newClient = new Client();
        newClient.setName(client.getName());
        newClient.setPhone(client.getPhone());
        newClient.setEmail(client.getEmail());
        newClient.setPasswordHash(client.getPasswordHash());
        newClient.setDefaultAddress(client.getDefaultAddress());
        newClient.setRegisteredAt(client.getRegisteredAt() != null ? client.getRegisteredAt() : LocalDate.now());
        newClient.setLoyaltyPoints(client.getLoyaltyPoints() != null ? client.getLoyaltyPoints() : 0);
        Client saved = clientRepository.save(newClient);
        log.info("Client created with ID: {}", saved.getId());
        return saved;
    }
    
    @Transactional
    public Client createClient(Client client, String password) {
        client.setPasswordHash(PasswordUtil.encode(password));
        return createClient(client);
    }
    
    @Transactional
    public Client updateClient(Integer id, Client clientData) {
        Client client = getClientById(id);
        if (clientData.getName() != null) {
            client.setName(clientData.getName());
        }
        if (clientData.getPhone() != null && !client.getPhone().equals(clientData.getPhone())) {
            if (clientRepository.existsByPhone(clientData.getPhone())) {
                throw new DuplicateEntityException("Client", "phone", clientData.getPhone());
            }
            client.setPhone(clientData.getPhone());
        }
        if (clientData.getDefaultAddress() != null) {
            client.setDefaultAddress(clientData.getDefaultAddress());
        }
        Client updated = clientRepository.save(client);
        log.info("Client {} updated", id);
        return updated;
    }
    
    public List<Order> getClientOrders(Integer clientId) {
        getClientById(clientId); 
        return orderRepository.findByClientIdOrderByCreatedAtDesc(clientId);
    }
}

