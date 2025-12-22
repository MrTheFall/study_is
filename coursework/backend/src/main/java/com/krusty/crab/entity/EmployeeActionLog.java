package com.krusty.crab.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "employee_action_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id",
        foreignKey = @ForeignKey(name = "fk_employee_action_logs_employee"))
    private Employee employee;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "entity_type", length = 32)
    private String entityType;

    @Column(name = "entity_id")
    private Integer entityId;

    @Column(name = "order_id")
    private Integer orderId;

    @Column(name = "from_value", length = 64)
    private String fromValue;

    @Column(name = "to_value", length = 64)
    private String toValue;

    @Column(name = "details")
    private String details;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
