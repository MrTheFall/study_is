package com.krusty.crab.repository;

import com.krusty.crab.entity.OrderItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Integer> {

    @Query("select oi from OrderItem oi join fetch oi.menuItem where oi.order.id = :orderId order by oi.id")
    List<OrderItem> findDetailedByOrderId(@Param("orderId") Integer orderId);

    @Query("select oi from OrderItem oi join fetch oi.menuItem "
            + "where oi.order.id in :orderIds order by oi.order.id, oi.id")
    List<OrderItem> findDetailedByOrderIds(@Param("orderIds") List<Integer> orderIds);
}
