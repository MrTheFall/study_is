package com.krusty.crab.repository;

import com.krusty.crab.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {
    
    List<Order> findByClientId(Integer clientId);
    
    List<Order> findByStatus(String status);
    
    @Query(value = "SELECT place_order(:clientId, :type, :deliveryAddress, :items::jsonb)", nativeQuery = true)
    Integer callPlaceOrder(
        @Param("clientId") Integer clientId,
        @Param("type") String type,
        @Param("deliveryAddress") String deliveryAddress,
        @Param("items") String items
    );
    
    @Query(value = "SELECT update_order_status(:orderId, :newStatus)", nativeQuery = true)
    void callUpdateOrderStatus(
        @Param("orderId") Integer orderId,
        @Param("newStatus") String newStatus
    );
    
    @Query(value = "SELECT * FROM get_kitchen_queue()", nativeQuery = true)
    List<Object[]> callGetKitchenQueue();
}

