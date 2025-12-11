package com.krusty.crab.repository;

import com.krusty.crab.entity.InventoryRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<InventoryRecord, Integer> {
    
    Optional<InventoryRecord> findByIngredientId(Integer ingredientId);
    
    @Query(value = "SELECT * FROM low_stock(:thresholdFactor)", nativeQuery = true)
    List<Object[]> callLowStock(@Param("thresholdFactor") Double thresholdFactor);
    
    @Query(value = "SELECT restock_ingredient(:ingredientId, :delta)", nativeQuery = true)
    void callRestockIngredient(
        @Param("ingredientId") Integer ingredientId,
        @Param("delta") Double delta
    );
}

