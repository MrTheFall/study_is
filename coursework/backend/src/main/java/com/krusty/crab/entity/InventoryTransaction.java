package com.krusty.crab.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_inventory_transactions_ingredient"))
    private Ingredient ingredient;

    @Column(name = "delta", nullable = false, precision = 14, scale = 3)
    private BigDecimal delta;

    @Column(name = "reason")
    private String reason;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id",
        foreignKey = @ForeignKey(name = "fk_inventory_transactions_employee"))
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id",
        foreignKey = @ForeignKey(name = "fk_inventory_transactions_order"))
    private Order order;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
