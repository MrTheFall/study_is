package com.krusty.crab.service;

import com.krusty.crab.entity.Client;
import com.krusty.crab.entity.Employee;
import com.krusty.crab.exception.EntityNotFoundException;
import com.krusty.crab.exception.ValidationException;
import com.krusty.crab.repository.ClientRepository;
import com.krusty.crab.repository.EmployeeRepository;
import com.krusty.crab.security.UserPrincipal;
import com.krusty.crab.security.UserType;
import com.krusty.crab.util.JwtUtil;
import com.krusty.crab.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final ClientRepository clientRepository;
    private final EmployeeRepository employeeRepository;
    private final JwtUtil jwtUtil;

    @Transactional(readOnly = true)
    public String loginClient(String email, String password) {
        Client client = clientRepository
                .findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Client with email '" + email + "' not found"));

        if (!PasswordUtil.matches(password, client.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        String token = jwtUtil.generateToken(email, UserType.CLIENT, client.getId(), null);

        log.info("Client {} logged in successfully", email);
        return token;
    }

    @Transactional(readOnly = true)
    public String loginEmployee(String login, String password) {
        Employee employee = employeeRepository
                .findByLogin(login)
                .orElseThrow(() -> new EntityNotFoundException("Employee with login '" + login + "' not found"));

        if (!PasswordUtil.matches(password, employee.getPasswordHash())) {
            throw new BadCredentialsException("Invalid login or password");
        }

        String roleName = employee.getRole() != null ? employee.getRole().getName() : null;
        String token = jwtUtil.generateToken(login, UserType.EMPLOYEE, employee.getId(), roleName);

        log.info("Employee {} logged in successfully with role {}", login, roleName);
        return token;
    }

    @Transactional(readOnly = true)
    public UserPrincipal getCurrentUser(Integer userId, UserType userType) {
        if (userType == UserType.CLIENT) {
            Client client =
                    clientRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("Client", userId));
            return new UserPrincipal(client.getId(), client.getEmail(), UserType.CLIENT, null);
        } else if (userType == UserType.EMPLOYEE) {
            Employee employee = employeeRepository
                    .findById(userId)
                    .orElseThrow(() -> new EntityNotFoundException("Employee", userId));
            String roleName = employee.getRole() != null ? employee.getRole().getName() : null;
            return new UserPrincipal(employee.getId(), employee.getLogin(), UserType.EMPLOYEE, roleName);
        } else {
            throw new ValidationException("Invalid user type: " + userType);
        }
    }
}
