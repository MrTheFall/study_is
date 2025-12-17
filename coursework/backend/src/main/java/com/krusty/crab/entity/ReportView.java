package com.krusty.crab.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "report_views")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id",
        foreignKey = @ForeignKey(name = "fk_report_views_employee"))
    private Employee employee;

    @Column(name = "report", nullable = false, length = 64)
    private String report;

    @Column(name = "from_ts")
    private LocalDateTime fromTs;

    @Column(name = "to_ts")
    private LocalDateTime toTs;

    @Column(name = "viewed_at", nullable = false)
    private LocalDateTime viewedAt;
}

