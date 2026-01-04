package com.krusty.crab.controller;

import com.krusty.crab.api.ShiftsApi;
import com.krusty.crab.dto.generated.AssignEmployeeToShiftRequest;
import com.krusty.crab.dto.generated.ShiftCreateRequest;
import com.krusty.crab.mapper.ShiftMapper;
import com.krusty.crab.service.EmployeeActionLogService;
import com.krusty.crab.service.ShiftService;
import com.krusty.crab.util.AuditActions;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('Manager')")
public class ShiftsController implements ShiftsApi {

    private final ShiftService shiftService;
    private final ShiftMapper shiftMapper;
    private final EmployeeActionLogService actionLogService;

    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.Shift> createShift(ShiftCreateRequest shiftCreateRequest) {
        log.info("Creating shift");
        com.krusty.crab.entity.Shift shift = shiftMapper.toEntity(shiftCreateRequest);
        com.krusty.crab.entity.Shift saved = shiftService.createShift(shift);
        String details = String.format(
                "date=%s, start=%s, end=%s", saved.getShiftDate(), saved.getStartTime(), saved.getEndTime());
        actionLogService.logCurrentEmployeeAction(
                AuditActions.SHIFT_CREATE, "shift", saved.getId(), null, null, null, details);
        com.krusty.crab.dto.generated.Shift dto = shiftMapper.toDto(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Override
    public ResponseEntity<List<com.krusty.crab.dto.generated.Shift>> getAllShifts(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
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
    public ResponseEntity<List<com.krusty.crab.dto.generated.EmployeeShift>> getShiftAssignments(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.info("Getting shift assignments, date: {}", date);
        List<com.krusty.crab.entity.EmployeeShift> assignments = shiftService.getShiftAssignments(date);
        return ResponseEntity.ok(shiftMapper.toEmployeeShiftDtoList(assignments));
    }

    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.Shift> getShiftById(Integer shiftId) {
        log.info("Getting shift by ID: {}", shiftId);
        com.krusty.crab.entity.Shift shift = shiftService.getShiftById(shiftId);
        com.krusty.crab.dto.generated.Shift dto = shiftMapper.toDto(shift);
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.Shift> updateShift(
            Integer shiftId, ShiftCreateRequest shiftCreateRequest) {
        log.info("Updating shift with ID: {}", shiftId);
        com.krusty.crab.entity.Shift shift = shiftService.getShiftById(shiftId);
        shiftMapper.updateEntityFromRequest(shiftCreateRequest, shift);
        com.krusty.crab.entity.Shift updated = shiftService.updateShift(shiftId, shift);
        String details = String.format(
                "date=%s, start=%s, end=%s", updated.getShiftDate(), updated.getStartTime(), updated.getEndTime());
        actionLogService.logCurrentEmployeeAction(
                AuditActions.SHIFT_UPDATE, "shift", shiftId, null, null, null, details);
        com.krusty.crab.dto.generated.Shift dto = shiftMapper.toDto(updated);
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<Void> deleteShift(Integer shiftId) {
        log.info("Deleting shift with ID: {}", shiftId);
        shiftService.deleteShift(shiftId);
        actionLogService.logCurrentEmployeeAction(AuditActions.SHIFT_DELETE, "shift", shiftId, null, null, null, null);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.EmployeeShift> assignEmployeeToShift(
            Integer shiftId, AssignEmployeeToShiftRequest assignEmployeeToShiftRequest) {
        log.info("Assigning employee {} to shift {}", assignEmployeeToShiftRequest.getEmployeeId(), shiftId);
        com.krusty.crab.entity.EmployeeShift employeeShift =
                shiftService.assignEmployeeToShift(assignEmployeeToShiftRequest.getEmployeeId(), shiftId);
        String details = "employeeId=" + assignEmployeeToShiftRequest.getEmployeeId();
        actionLogService.logCurrentEmployeeAction(
                AuditActions.SHIFT_ASSIGN, "shift", shiftId, null, null, null, details);
        com.krusty.crab.dto.generated.EmployeeShift dto = shiftMapper.toDto(employeeShift);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Override
    public ResponseEntity<Void> removeEmployeeFromShift(Integer employeeShiftId) {
        log.info("Removing employeeShift assignment {}", employeeShiftId);
        shiftService.removeEmployeeFromShift(employeeShiftId);
        actionLogService.logCurrentEmployeeAction(
                AuditActions.SHIFT_UNASSIGN, "employee_shift", employeeShiftId, null, null, null, null);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
