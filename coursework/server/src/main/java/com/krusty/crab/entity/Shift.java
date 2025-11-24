package com.krusty.crab.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "shifts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shift {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(name = "shift_date", nullable = false)
    private LocalDate shiftDate;
    
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;
    
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
    
    @Column(name = "note", length = 255)
    private String note;
    
    @OneToMany(mappedBy = "shift", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<EmployeeShift> employeeShifts = new ArrayList<>();
}

