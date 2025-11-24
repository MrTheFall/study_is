package com.krusty.crab.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ingredients", uniqueConstraints = @UniqueConstraint(name = "uq_ingredients_name", columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ingredient {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(name = "name", nullable = false, length = 255)
    private String name;
    
    @Column(name = "unit", nullable = false, length = 32)
    private String unit;
    
    @Column(name = "cost_per_unit", nullable = false, precision = 14, scale = 4)
    @Builder.Default
    private BigDecimal costPerUnit = BigDecimal.ZERO;
    
    @Column(name = "min_threshold", nullable = false, precision = 14, scale = 3)
    @Builder.Default
    private BigDecimal minThreshold = BigDecimal.ZERO;
    
    @OneToMany(mappedBy = "ingredient", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<IngredientUsage> ingredientUsages = new ArrayList<>();
    
    @OneToOne(mappedBy = "ingredient", cascade = CascadeType.ALL, orphanRemoval = true)
    private InventoryRecord inventoryRecord;
}

