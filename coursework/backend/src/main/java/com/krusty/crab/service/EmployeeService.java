package com.krusty.crab.service;

import com.krusty.crab.entity.Employee;
import com.krusty.crab.entity.Role;
import com.krusty.crab.exception.DuplicateEntityException;
import com.krusty.crab.exception.EntityNotFoundException;
import com.krusty.crab.repository.EmployeeRepository;
import com.krusty.crab.repository.RoleRepository;
import com.krusty.crab.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    public Employee createEmployee(Employee employee, String password) {
        if (employeeRepository.existsByLogin(employee.getLogin())) {
            throw new DuplicateEntityException("Employee", "login", employee.getLogin());
        }

        Role role = roleRepository.findById(employee.getRole().getId())
            .orElseThrow(() -> new EntityNotFoundException("Role", employee.getRole().getId()));
        
        Employee newEmployee = new Employee();
        newEmployee.setFullName(employee.getFullName());
        newEmployee.setLogin(employee.getLogin());
        newEmployee.setRole(role);
        newEmployee.setSalary(employee.getSalary() != null ? employee.getSalary() : java.math.BigDecimal.ZERO);
        newEmployee.setContactPhone(employee.getContactPhone());
        newEmployee.setHiredAt(employee.getHiredAt() != null ? employee.getHiredAt() : LocalDateTime.now());
        newEmployee.setPasswordHash(password != null && !password.isEmpty() 
            ? PasswordUtil.encode(password) 
            : employee.getPasswordHash());
        
        Employee saved = employeeRepository.save(newEmployee);
        log.info("Employee created with ID: {}", saved.getId());
        return saved;
    }
    
    @Transactional
    public Employee createEmployee(Employee employee) {
        return createEmployee(employee, null);
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

