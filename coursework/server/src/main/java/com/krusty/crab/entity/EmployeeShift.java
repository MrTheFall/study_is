package com.krusty.crab.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "employee_shifts",
    uniqueConstraints = @UniqueConstraint(name = "uq_employee_shifts_unique", columnNames = {"employee_id", "shift_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeShift {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_employee_shifts_employee"))
    private Employee employee;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_employee_shifts_shift"))
    private Shift shift;
    
    @Column(name = "status", length = 64)
    private String status;
}

