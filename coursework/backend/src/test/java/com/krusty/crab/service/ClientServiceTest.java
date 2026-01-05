package com.krusty.crab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krusty.crab.entity.Client;
import com.krusty.crab.entity.Order;
import com.krusty.crab.exception.DuplicateEntityException;
import com.krusty.crab.exception.EntityNotFoundException;
import com.krusty.crab.exception.ValidationException;
import com.krusty.crab.repository.ClientRepository;
import com.krusty.crab.repository.OrderRepository;
import com.krusty.crab.util.PasswordUtil;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private ClientService clientService;

    @Test
    void lookupClient_requiresEmailOrPhone() {
        assertThatThrownBy(() -> clientService.lookupClient(" ", " "))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("email or phone is required");
    }

    @Test
    void lookupClient_returnsByEmail() {
        Client client = new Client();
        client.setId(1);
        when(clientRepository.findByEmail("client@example.com"))
                .thenReturn(Optional.of(client));

        Client result = clientService.lookupClient("client@example.com", null);

        assertThat(result.getId()).isEqualTo(1);
    }

    @Test
    void lookupClient_returnsByPhoneWhenEmailMissing() {
        Client client = new Client();
        client.setId(2);
        when(clientRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());
        when(clientRepository.findByPhone("123"))
                .thenReturn(Optional.of(client));

        Client result = clientService.lookupClient("missing@example.com", "123");

        assertThat(result.getId()).isEqualTo(2);
    }

    @Test
    void lookupClient_throwsWhenEmailNotFound() {
        when(clientRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.lookupClient("missing@example.com", null))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("email");
    }

    @Test
    void createClient_rejectsDuplicateEmail() {
        Client client = new Client();
        client.setEmail("client@example.com");
        when(clientRepository.existsByEmail("client@example.com")).thenReturn(true);

        assertThatThrownBy(() -> clientService.createClient(client))
                .isInstanceOf(DuplicateEntityException.class)
                .hasMessageContaining("email");

        verify(clientRepository, never()).existsByPhone(any());
    }

    @Test
    void createClient_rejectsDuplicatePhone() {
        Client client = new Client();
        client.setEmail("client@example.com");
        client.setPhone("123");
        when(clientRepository.existsByEmail("client@example.com")).thenReturn(false);
        when(clientRepository.existsByPhone("123")).thenReturn(true);

        assertThatThrownBy(() -> clientService.createClient(client))
                .isInstanceOf(DuplicateEntityException.class)
                .hasMessageContaining("phone");
    }

    @Test
    void createClient_setsDefaultsAndSaves() {
        Client client = new Client();
        client.setName("Client");
        client.setPhone("123");
        client.setEmail("client@example.com");
        client.setPasswordHash("hash");
        client.setDefaultAddress("Street 1");

        when(clientRepository.existsByEmail("client@example.com")).thenReturn(false);
        when(clientRepository.existsByPhone("123")).thenReturn(false);
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Client result = clientService.createClient(client);

        assertThat(result.getName()).isEqualTo("Client");
        assertThat(result.getLoyaltyPoints()).isEqualTo(0);
        assertThat(result.getRegisteredAt()).isEqualTo(LocalDate.now());
    }

    @Test
    void createClient_withPasswordHashesAndSaves() {
        Client client = new Client();
        client.setName("Client");
        client.setPhone("123");
        client.setEmail("client@example.com");

        when(clientRepository.existsByEmail("client@example.com")).thenReturn(false);
        when(clientRepository.existsByPhone("123")).thenReturn(false);
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Client result = clientService.createClient(client, "secret");

        assertThat(PasswordUtil.matches("secret", result.getPasswordHash())).isTrue();
    }

    @Test
    void updateClient_updatesFieldsAndValidatesPhone() {
        Client existing = new Client();
        existing.setId(5);
        existing.setName("Old");
        existing.setPhone("111");
        existing.setDefaultAddress("A");

        Client updates = new Client();
        updates.setName("New");
        updates.setPhone("222");
        updates.setDefaultAddress("B");

        when(clientRepository.findById(5)).thenReturn(Optional.of(existing));
        when(clientRepository.existsByPhone("222")).thenReturn(false);
        when(clientRepository.save(existing)).thenReturn(existing);

        Client result = clientService.updateClient(5, updates);

        assertThat(result.getName()).isEqualTo("New");
        assertThat(result.getPhone()).isEqualTo("222");
        assertThat(result.getDefaultAddress()).isEqualTo("B");
    }

    @Test
    void updateClient_rejectsDuplicatePhone() {
        Client existing = new Client();
        existing.setId(5);
        existing.setPhone("111");

        Client updates = new Client();
        updates.setPhone("222");

        when(clientRepository.findById(5)).thenReturn(Optional.of(existing));
        when(clientRepository.existsByPhone("222")).thenReturn(true);

        assertThatThrownBy(() -> clientService.updateClient(5, updates))
                .isInstanceOf(DuplicateEntityException.class)
                .hasMessageContaining("phone");
    }

    @Test
    void getClientOrders_returnsOrders() {
        Client existing = new Client();
        existing.setId(7);
        when(clientRepository.findById(7)).thenReturn(Optional.of(existing));
        when(orderRepository.findByClientIdOrderByCreatedAtDesc(7)).thenReturn(List.of(new Order()));

        List<Order> orders = clientService.getClientOrders(7);

        assertThat(orders).hasSize(1);
    }

    @Test
    void changePassword_rejectsIncorrectCurrentPassword() {
        Client existing = new Client();
        existing.setId(8);
        existing.setPasswordHash(PasswordUtil.encode("correct"));
        when(clientRepository.findById(8)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> clientService.changePassword(8, "wrong", "new"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("currentPassword");

        verify(clientRepository, never()).save(any(Client.class));
    }

    @Test
    void changePassword_updatesPasswordHash() {
        Client existing = new Client();
        existing.setId(9);
        existing.setPasswordHash(PasswordUtil.encode("old"));
        when(clientRepository.findById(9)).thenReturn(Optional.of(existing));
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> invocation.getArgument(0));

        clientService.changePassword(9, "old", "new");

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(captor.capture());
        assertThat(PasswordUtil.matches("new", captor.getValue().getPasswordHash())).isTrue();
    }
}
