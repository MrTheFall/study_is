package com.krusty.crab.repository;

import com.krusty.crab.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AnalyticsRepository extends JpaRepository<Order, Integer> {
    
    @Query(value = "SELECT * FROM sales_summary(:fromTs, :toTs)", nativeQuery = true)
    List<Object[]> callSalesSummary(
        @Param("fromTs") LocalDateTime fromTs,
        @Param("toTs") LocalDateTime toTs
    );
    
    @Query(value = "SELECT * FROM top_menu_items(:fromTs, :toTs, :limit)", nativeQuery = true)
    List<Object[]> callTopMenuItems(
        @Param("fromTs") LocalDateTime fromTs,
        @Param("toTs") LocalDateTime toTs,
        @Param("limit") Integer limit
    );
}

