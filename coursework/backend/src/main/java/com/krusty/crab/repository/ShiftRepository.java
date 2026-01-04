package com.krusty.crab.repository;

import com.krusty.crab.entity.Shift;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShiftRepository extends JpaRepository<Shift, Integer> {
    List<Shift> findByShiftDate(LocalDate shiftDate);
}
