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
    
    List<Order> findByClientIdOrderByCreatedAtDesc(Integer clientId);
    
    List<Order> findByStatus(String status);

    @Query("select count(o.id) from Order o where o.client.id = :clientId and o.id in :orderIds")
    long countOwnedByClient(
        @Param("clientId") Integer clientId,
        @Param("orderIds") List<Integer> orderIds
    );

    boolean existsByCourier_IdAndStatusIn(Integer courierId, List<com.krusty.crab.entity.enums.OrderStatus> statuses);

    boolean existsByCourier_IdAndStatusInAndIdNot(
        Integer courierId,
        List<com.krusty.crab.entity.enums.OrderStatus> statuses,
        Integer orderId
    );
    
    @Query(value = "SELECT place_order(:clientId, :type, :deliveryAddress, :paymentMethod, CAST(:items AS jsonb))", nativeQuery = true)
    Integer callPlaceOrder(
        @Param("clientId") Integer clientId,
        @Param("type") String type,
        @Param("deliveryAddress") String deliveryAddress,
        @Param("paymentMethod") String paymentMethod,
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
