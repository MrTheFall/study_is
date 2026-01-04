package com.krusty.crab.repository;

import com.krusty.crab.entity.Ingredient;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, Integer> {
    Optional<Ingredient> findByName(String name);

    boolean existsByName(String name);
}
