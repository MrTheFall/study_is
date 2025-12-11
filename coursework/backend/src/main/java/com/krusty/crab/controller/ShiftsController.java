package com.krusty.crab.controller;

import com.krusty.crab.api.ShiftsApi;
import com.krusty.crab.dto.generated.AssignEmployeeToShiftRequest;
import com.krusty.crab.dto.generated.ShiftCreateRequest;
import com.krusty.crab.mapper.ShiftMapper;
import com.krusty.crab.service.ShiftService;
import com.krusty.crab.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ShiftsController implements ShiftsApi {
    
    private final ShiftService shiftService;
    private final ShiftMapper shiftMapper;
    
    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.Shift> createShift(ShiftCreateRequest shiftCreateRequest) {
        SecurityUtil.requireRole("Manager");
        log.info("Creating shift");
        com.krusty.crab.entity.Shift shift = shiftMapper.toEntity(shiftCreateRequest);
        com.krusty.crab.entity.Shift saved = shiftService.createShift(shift);
        com.krusty.crab.dto.generated.Shift dto = shiftMapper.toDto(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
    
    @Override
    public ResponseEntity<List<com.krusty.crab.dto.generated.Shift>> getAllShifts(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.info("Getting all shifts, date: {}", date);
        List<com.krusty.crab.entity.Shift> shifts;
        if (date != null) {
            shifts = shiftService.getShiftsByDate(date);
        } else {
            shifts = shiftService.getAllShifts();
        }
        return ResponseEntity.ok(shiftMapper.toDtoList(shifts));
    }
    
    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.Shift> getShiftById(Integer shiftId) {
        log.info("Getting shift by ID: {}", shiftId);
        com.krusty.crab.entity.Shift shift = shiftService.getShiftById(shiftId);
        com.krusty.crab.dto.generated.Shift dto = shiftMapper.toDto(shift);
        return ResponseEntity.ok(dto);
    }
    
    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.Shift> updateShift(Integer shiftId, ShiftCreateRequest shiftCreateRequest) {
        SecurityUtil.requireRole("Manager");
        log.info("Updating shift with ID: {}", shiftId);
        com.krusty.crab.entity.Shift shift = shiftService.getShiftById(shiftId);
        shiftMapper.updateEntityFromRequest(shiftCreateRequest, shift);
        com.krusty.crab.entity.Shift updated = shiftService.updateShift(shiftId, shift);
        com.krusty.crab.dto.generated.Shift dto = shiftMapper.toDto(updated);
        return ResponseEntity.ok(dto);
    }
    
    @Override
    public ResponseEntity<Void> deleteShift(Integer shiftId) {
        SecurityUtil.requireRole("Manager");
        log.info("Deleting shift with ID: {}", shiftId);
        shiftService.deleteShift(shiftId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
    
    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.EmployeeShift> assignEmployeeToShift(Integer shiftId, AssignEmployeeToShiftRequest assignEmployeeToShiftRequest) {
        SecurityUtil.requireRole("Manager");
        log.info("Assigning employee {} to shift {}", assignEmployeeToShiftRequest.getEmployeeId(), shiftId);
        com.krusty.crab.entity.EmployeeShift employeeShift = shiftService.assignEmployeeToShift(
            assignEmployeeToShiftRequest.getEmployeeId(), 
            shiftId
        );
        com.krusty.crab.dto.generated.EmployeeShift dto = shiftMapper.toDto(employeeShift);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
}

