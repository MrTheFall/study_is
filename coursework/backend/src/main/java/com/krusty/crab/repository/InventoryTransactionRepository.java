package com.krusty.crab.repository;

import com.krusty.crab.entity.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Integer> {

    @Query(value = """
        select *
          from inventory_transactions it
         where (:ingredientId is null or it.ingredient_id = :ingredientId)
         order by it.created_at desc
         limit :limit
         offset :offset
        """, nativeQuery = true)
    List<InventoryTransaction> findRecent(
        @Param("ingredientId") Integer ingredientId,
        @Param("limit") int limit,
        @Param("offset") int offset
    );
}

