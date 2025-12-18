package com.krusty.crab.repository;

import com.krusty.crab.entity.EmployeeShift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface EmployeeShiftRepository extends JpaRepository<EmployeeShift, Integer> {
    List<EmployeeShift> findByEmployeeId(Integer employeeId);
    List<EmployeeShift> findByShiftId(Integer shiftId);

    @Query("""
        select es
          from EmployeeShift es
          join fetch es.employee
          join fetch es.shift
         where (:date is null or es.shift.shiftDate = :date)
         order by es.shift.id, es.employee.id, es.id
        """)
    List<EmployeeShift> findDetailedByShiftDate(@Param("date") LocalDate date);
}
