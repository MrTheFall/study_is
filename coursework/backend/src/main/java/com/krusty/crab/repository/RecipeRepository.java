package com.krusty.crab.repository;

import com.krusty.crab.entity.Recipe;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, Integer> {
    Optional<Recipe> findByMenuItemId(Integer menuItemId);
}
