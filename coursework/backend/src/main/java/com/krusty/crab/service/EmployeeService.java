package com.krusty.crab.service;

import com.krusty.crab.entity.Employee;
import com.krusty.crab.exception.DuplicateEntityException;
import com.krusty.crab.exception.EntityNotFoundException;
import com.krusty.crab.repository.EmployeeRepository;
import com.krusty.crab.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeService {
    
    private final EmployeeRepository employeeRepository;
    private final RoleRepository roleRepository;
    
    public Employee getEmployeeById(Integer id) {
        return employeeRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Employee", id));
    }
    
    public Employee getEmployeeByLogin(String login) {
        return employeeRepository.findByLogin(login)
            .orElseThrow(() -> new EntityNotFoundException("Employee", "login", login));
    }
    
    public List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
    }
    
    @Transactional
    public Employee createEmployee(Employee employee) {
        if (employeeRepository.existsByLogin(employee.getLogin())) {
            throw new DuplicateEntityException("Employee", "login", employee.getLogin());
        }
        // Проверяем, что роль существует
        roleRepository.findById(employee.getRole().getId())
            .orElseThrow(() -> new EntityNotFoundException("Role", employee.getRole().getId()));
        
        Employee saved = employeeRepository.save(employee);
        log.info("Employee created with ID: {}", saved.getId());
        return saved;
    }
    
    @Transactional
    public Employee updateEmployee(Integer id, Employee employeeData) {
        Employee employee = getEmployeeById(id);
        if (employeeData.getFullName() != null) {
            employee.setFullName(employeeData.getFullName());
        }
        if (employeeData.getLogin() != null && !employee.getLogin().equals(employeeData.getLogin())) {
            if (employeeRepository.existsByLogin(employeeData.getLogin())) {
                throw new DuplicateEntityException("Employee", "login", employeeData.getLogin());
            }
            employee.setLogin(employeeData.getLogin());
        }
        if (employeeData.getPasswordHash() != null) {
            employee.setPasswordHash(employeeData.getPasswordHash());
        }
        if (employeeData.getRole() != null && employeeData.getRole().getId() != null) {
            roleRepository.findById(employeeData.getRole().getId())
                .orElseThrow(() -> new EntityNotFoundException("Role", employeeData.getRole().getId()));
            employee.setRole(employeeData.getRole());
        }
        if (employeeData.getSalary() != null) {
            employee.setSalary(employeeData.getSalary());
        }
        if (employeeData.getContactPhone() != null) {
            employee.setContactPhone(employeeData.getContactPhone());
        }
        Employee updated = employeeRepository.save(employee);
        log.info("Employee {} updated", id);
        return updated;
    }
    
    @Transactional
    public void deleteEmployee(Integer id) {
        Employee employee = getEmployeeById(id);
        employeeRepository.delete(employee);
        log.info("Employee {} deleted", id);
    }
}

