package com.krusty.crab.repository;

import com.krusty.crab.entity.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, Integer> {
    Optional<Ingredient> findByName(String name);
    boolean existsByName(String name);
}

