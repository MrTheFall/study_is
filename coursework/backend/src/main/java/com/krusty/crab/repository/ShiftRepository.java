package com.krusty.crab.repository;

import com.krusty.crab.entity.Shift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ShiftRepository extends JpaRepository<Shift, Integer> {
    List<Shift> findByShiftDate(LocalDate shiftDate);
}

