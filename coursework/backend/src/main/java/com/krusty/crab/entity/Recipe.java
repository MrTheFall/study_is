package com.krusty.crab.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "recipes", uniqueConstraints = @UniqueConstraint(name = "uq_recipes_menu_item", columnNames = "menu_item_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recipe {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_item_id", nullable = false, unique = true,
        foreignKey = @ForeignKey(name = "fk_recipes_menu_item"))
    private MenuItem menuItem;
    
    @Column(name = "name", nullable = false, length = 255)
    private String name;
    
    @Column(name = "description", columnDefinition = "text")
    private String description;
    
    @Column(name = "preparation_time", nullable = false)
    @Builder.Default
    private Integer preparationTime = 0;
    
    @Column(name = "servings", nullable = false)
    @Builder.Default
    private Integer servings = 1;
    
    @Column(name = "instructions", columnDefinition = "text")
    private String instructions;
    
    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<IngredientUsage> ingredientUsages = new ArrayList<>();
}

