package com.krusty.crab.service;

import com.krusty.crab.entity.EmployeeShift;
import com.krusty.crab.entity.Shift;
import com.krusty.crab.exception.EntityNotFoundException;
import com.krusty.crab.exception.ShiftException;
import com.krusty.crab.repository.EmployeeRepository;
import com.krusty.crab.repository.EmployeeShiftRepository;
import com.krusty.crab.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShiftService {
    
    private final ShiftRepository shiftRepository;
    private final EmployeeShiftRepository employeeShiftRepository;
    private final EmployeeRepository employeeRepository;
    
    public Shift getShiftById(Integer id) {
        return shiftRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Shift", id));
    }
    
    public List<Shift> getShiftsByDate(LocalDate date) {
        return shiftRepository.findByShiftDate(date);
    }
    
    public List<Shift> getAllShifts() {
        return shiftRepository.findAll();
    }
    
    @Transactional
    public Shift createShift(Shift shift) {
        if (shift.getStartTime() != null && shift.getEndTime() != null) {
            if (!shift.getStartTime().isBefore(shift.getEndTime())) {
                throw new ShiftException("Start time must be before end time");
            }
        }
        try {
            Shift saved = shiftRepository.save(shift);
            log.info("Shift created with ID: {}", saved.getId());
            return saved;
        } catch (Exception e) {
            throw new ShiftException("Failed to create shift: " + e.getMessage(), e);
        }
    }
    
    @Transactional
    public Shift updateShift(Integer id, Shift shiftData) {
        Shift shift = getShiftById(id);
        if (shiftData.getShiftDate() != null) {
            shift.setShiftDate(shiftData.getShiftDate());
        }
        if (shiftData.getStartTime() != null) {
            shift.setStartTime(shiftData.getStartTime());
        }
        if (shiftData.getEndTime() != null) {
            shift.setEndTime(shiftData.getEndTime());
        }
        if (shiftData.getNote() != null) {
            shift.setNote(shiftData.getNote());
        }
        
        if (shift.getStartTime() != null && shift.getEndTime() != null) {
            if (!shift.getStartTime().isBefore(shift.getEndTime())) {
                throw new ShiftException("Start time must be before end time");
            }
        }
        
        try {
            Shift updated = shiftRepository.save(shift);
            log.info("Shift {} updated", id);
            return updated;
        } catch (Exception e) {
            throw new ShiftException("Failed to update shift: " + e.getMessage(), e);
        }
    }
    
    @Transactional
    public void deleteShift(Integer id) {
        Shift shift = getShiftById(id);
        shiftRepository.delete(shift);
        log.info("Shift {} deleted", id);
    }
    
    public List<EmployeeShift> getEmployeeShifts(Integer employeeId) {
        employeeRepository.findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("Employee", employeeId));
        return employeeShiftRepository.findByEmployeeId(employeeId);
    }
    
    @Transactional
    public EmployeeShift assignEmployeeToShift(Integer employeeId, Integer shiftId) {
        employeeRepository.findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("Employee", employeeId));
        Shift shift = getShiftById(shiftId);
        
        List<EmployeeShift> existing = employeeShiftRepository.findByEmployeeId(employeeId);
        if (existing.stream().anyMatch(es -> es.getShift().getId().equals(shiftId))) {
            throw new ShiftException("Employee is already assigned to this shift");
        }
        
        try {
            EmployeeShift employeeShift = EmployeeShift.builder()
                .employee(employeeRepository.getReferenceById(employeeId))
                .shift(shift)
                .status("assigned")
                .build();
            
            EmployeeShift saved = employeeShiftRepository.save(employeeShift);
            log.info("Employee {} assigned to shift {}", employeeId, shiftId);
            return saved;
        } catch (Exception e) {
            throw new ShiftException("Failed to assign employee to shift: " + e.getMessage(), e);
        }
    }
    
    @Transactional
    public void removeEmployeeFromShift(Integer employeeShiftId) {
        EmployeeShift employeeShift = employeeShiftRepository.findById(employeeShiftId)
            .orElseThrow(() -> new EntityNotFoundException("EmployeeShift", employeeShiftId));
        employeeShiftRepository.delete(employeeShift);
        log.info("EmployeeShift {} deleted", employeeShiftId);
    }
}

