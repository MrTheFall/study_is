package com.krusty.crab.controller;

import com.krusty.crab.api.EmployeesApi;
import com.krusty.crab.dto.generated.EmployeeCreateRequest;
import com.krusty.crab.entity.Employee;
import com.krusty.crab.mapper.EmployeeMapper;
import com.krusty.crab.mapper.ShiftMapper;
import com.krusty.crab.service.EmployeeService;
import com.krusty.crab.service.ShiftService;
import com.krusty.crab.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class EmployeesController implements EmployeesApi {
    
    private final EmployeeService employeeService;
    private final ShiftService shiftService;
    private final EmployeeMapper employeeMapper;
    private final ShiftMapper shiftMapper;
    
    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.Employee> createEmployee(EmployeeCreateRequest employeeCreateRequest) {
        SecurityUtil.requireRole("Manager");
        log.info("Creating employee: {}", employeeCreateRequest.getLogin());
        Employee employee = employeeMapper.toEntityWithPassword(employeeCreateRequest, employeeCreateRequest.getPassword());
        Employee saved = employeeService.createEmployee(employee);
        com.krusty.crab.dto.generated.Employee dto = employeeMapper.toDto(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
    
    @Override
    public ResponseEntity<List<com.krusty.crab.dto.generated.Employee>> getAllEmployees() {
        log.info("Getting all employees");
        List<Employee> employees = employeeService.getAllEmployees();
        return ResponseEntity.ok(employeeMapper.toDtoList(employees));
    }
    
    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.Employee> getEmployeeById(Integer employeeId) {
        log.info("Getting employee by ID: {}", employeeId);
        Employee employee = employeeService.getEmployeeById(employeeId);
        com.krusty.crab.dto.generated.Employee dto = employeeMapper.toDto(employee);
        return ResponseEntity.ok(dto);
    }
    
    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.Employee> updateEmployee(Integer employeeId, EmployeeCreateRequest employeeCreateRequest) {
        SecurityUtil.requireRole("Manager");
        log.info("Updating employee with ID: {}", employeeId);
        Employee employee = employeeService.getEmployeeById(employeeId);
        employeeMapper.updateEntityWithPassword(employeeCreateRequest, employee);
        Employee updated = employeeService.updateEmployee(employeeId, employee);
        com.krusty.crab.dto.generated.Employee dto = employeeMapper.toDto(updated);
        return ResponseEntity.ok(dto);
    }
    
    @Override
    public ResponseEntity<Void> deleteEmployee(Integer employeeId) {
        SecurityUtil.requireRole("Manager");
        log.info("Deleting employee with ID: {}", employeeId);
        employeeService.deleteEmployee(employeeId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
    
    @Override
    public ResponseEntity<List<com.krusty.crab.dto.generated.EmployeeShift>> getEmployeeShifts(Integer employeeId) {
        log.info("Getting shifts for employee ID: {}", employeeId);
        List<com.krusty.crab.entity.EmployeeShift> shifts = shiftService.getEmployeeShifts(employeeId);
        return ResponseEntity.ok(shiftMapper.toEmployeeShiftDtoList(shifts));
    }
}

