package com.krusty.crab.repository;

import com.krusty.crab.entity.EmployeeShift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeShiftRepository extends JpaRepository<EmployeeShift, Integer> {
    List<EmployeeShift> findByEmployeeId(Integer employeeId);
    List<EmployeeShift> findByShiftId(Integer shiftId);
}

