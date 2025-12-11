package com.krusty.crab.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false, unique = true,
        foreignKey = @ForeignKey(name = "fk_inventory_ingredient"))
    private Ingredient ingredient;
    
    @Column(name = "quantity", nullable = false, precision = 14, scale = 3)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ZERO;
    
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;
}

