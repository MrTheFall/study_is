package com.krusty.crab.repository;

import com.krusty.crab.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Integer> {
    List<MenuItem> findByAvailableTrue();
    List<MenuItem> findByNameContainingIgnoreCase(String name);

    @Query(value = """
        with per_ingredient as (
            select r.menu_item_id,
                   iu.ingredient_id,
                   round(sum(iu.quantity_required) / greatest(r.servings, 1)::numeric, 3) as required_per_unit
              from recipes r
              join ingredient_usages iu on iu.recipe_id = r.id
             where r.menu_item_id in (:menuItemIds)
             group by r.menu_item_id, iu.ingredient_id, r.servings
        )
        select pi.menu_item_id
          from per_ingredient pi
     left join inventory_records ir on ir.ingredient_id = pi.ingredient_id
         group by pi.menu_item_id
        having bool_or(coalesce(ir.quantity, 0) < pi.required_per_unit)
        """, nativeQuery = true)
    List<Integer> findOutOfStockMenuItemIds(@Param("menuItemIds") List<Integer> menuItemIds);
}
