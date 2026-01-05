package com.krusty.crab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krusty.crab.entity.Client;
import com.krusty.crab.entity.Employee;
import com.krusty.crab.entity.Role;
import com.krusty.crab.exception.EntityNotFoundException;
import com.krusty.crab.exception.ValidationException;
import com.krusty.crab.repository.ClientRepository;
import com.krusty.crab.repository.EmployeeRepository;
import com.krusty.crab.security.UserPrincipal;
import com.krusty.crab.security.UserType;
import com.krusty.crab.util.JwtUtil;
import com.krusty.crab.util.PasswordUtil;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    @Test
    void loginClient_returnsTokenWhenPasswordMatches() {
        Client client = new Client();
        client.setId(1);
        client.setEmail("client@example.com");
        client.setPasswordHash(PasswordUtil.encode("secret"));
        when(clientRepository.findByEmail("client@example.com")).thenReturn(Optional.of(client));
        when(jwtUtil.generateToken("client@example.com", UserType.CLIENT, 1, null)).thenReturn("token");

        String token = authService.loginClient("client@example.com", "secret");

        assertThat(token).isEqualTo("token");
        verify(jwtUtil).generateToken("client@example.com", UserType.CLIENT, 1, null);
    }

    @Test
    void loginClient_rejectsWrongPassword() {
        Client client = new Client();
        client.setEmail("client@example.com");
        client.setPasswordHash(PasswordUtil.encode("secret"));
        when(clientRepository.findByEmail("client@example.com")).thenReturn(Optional.of(client));

        assertThatThrownBy(() -> authService.loginClient("client@example.com", "wrong"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginClient_throwsWhenMissing() {
        when(clientRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.loginClient("missing@example.com", "secret"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Client with email");
    }

    @Test
    void loginEmployee_returnsTokenWithRole() {
        Employee employee = new Employee();
        employee.setId(5);
        employee.setLogin("emp");
        employee.setPasswordHash(PasswordUtil.encode("pass"));
        Role role = new Role();
        role.setName("MANAGER");
        employee.setRole(role);
        when(employeeRepository.findByLogin("emp")).thenReturn(Optional.of(employee));
        when(jwtUtil.generateToken("emp", UserType.EMPLOYEE, 5, "MANAGER")).thenReturn("token");

        String token = authService.loginEmployee("emp", "pass");

        assertThat(token).isEqualTo("token");
        verify(jwtUtil).generateToken("emp", UserType.EMPLOYEE, 5, "MANAGER");
    }

    @Test
    void getCurrentUser_returnsClientPrincipal() {
        Client client = new Client();
        client.setId(2);
        client.setEmail("client@example.com");
        when(clientRepository.findById(2)).thenReturn(Optional.of(client));

        UserPrincipal principal = authService.getCurrentUser(2, UserType.CLIENT);

        assertThat(principal.getUserId()).isEqualTo(2);
        assertThat(principal.getUsername()).isEqualTo("client@example.com");
        assertThat(principal.getUserType()).isEqualTo(UserType.CLIENT);
    }

    @Test
    void getCurrentUser_returnsEmployeePrincipal() {
        Employee employee = new Employee();
        employee.setId(3);
        employee.setLogin("emp");
        Role role = new Role();
        role.setName("ADMIN");
        employee.setRole(role);
        when(employeeRepository.findById(3)).thenReturn(Optional.of(employee));

        UserPrincipal principal = authService.getCurrentUser(3, UserType.EMPLOYEE);

        assertThat(principal.getUserId()).isEqualTo(3);
        assertThat(principal.getUsername()).isEqualTo("emp");
        assertThat(principal.getRole()).isEqualTo("ADMIN");
    }

    @Test
    void getCurrentUser_rejectsUnknownType() {
        assertThatThrownBy(() -> authService.getCurrentUser(1, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid user type");
    }
}
