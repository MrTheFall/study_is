package com.krusty.crab.repository;

import com.krusty.crab.entity.Order;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsRepository extends JpaRepository<Order, Integer> {

    @Query(value = "SELECT * FROM sales_summary(:fromTs, :toTs)", nativeQuery = true)
    List<Object[]> callSalesSummary(@Param("fromTs") LocalDateTime fromTs, @Param("toTs") LocalDateTime toTs);

    @Query(value = "SELECT * FROM top_menu_items(:fromTs, :toTs, :limit)", nativeQuery = true)
    List<Object[]> callTopMenuItems(
            @Param("fromTs") LocalDateTime fromTs, @Param("toTs") LocalDateTime toTs, @Param("limit") Integer limit);

    @Query(value = "SELECT * FROM sales_by_employee(:fromTs, :toTs)", nativeQuery = true)
    List<Object[]> callSalesByEmployee(@Param("fromTs") LocalDateTime fromTs, @Param("toTs") LocalDateTime toTs);

    @Query(value = "SELECT * FROM sales_by_time_of_day(:fromTs, :toTs)", nativeQuery = true)
    List<Object[]> callSalesByTimeOfDay(@Param("fromTs") LocalDateTime fromTs, @Param("toTs") LocalDateTime toTs);

    @Query(value = "SELECT * FROM financial_summary(:fromTs, :toTs)", nativeQuery = true)
    List<Object[]> callFinancialSummary(@Param("fromTs") LocalDateTime fromTs, @Param("toTs") LocalDateTime toTs);
}
