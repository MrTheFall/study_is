package com.krusty.crab.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "employees", uniqueConstraints = @UniqueConstraint(name = "uq_employees_login", columnNames = "login"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;
    
    @Column(name = "login", nullable = false, length = 128)
    private String login;
    
    @Column(name = "password_hash", nullable = false, columnDefinition = "text")
    private String passwordHash;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false, foreignKey = @ForeignKey(name = "fk_employees_role"))
    private Role role;
    
    @Column(name = "salary", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal salary = BigDecimal.ZERO;
    
    @Column(name = "contact_phone", length = 64)
    private String contactPhone;
    
    @Column(name = "hired_at", nullable = false)
    private LocalDateTime hiredAt;
    
    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<EmployeeShift> employeeShifts = new ArrayList<>();
}

