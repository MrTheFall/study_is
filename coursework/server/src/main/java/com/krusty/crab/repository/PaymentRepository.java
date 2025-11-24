package com.krusty.crab.repository;

import com.krusty.crab.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {
    
    Optional<Payment> findByOrderId(Integer orderId);
    
    boolean existsByOrderId(Integer orderId);
    
    @Query(value = "SELECT process_payment(:orderId, :method)", nativeQuery = true)
    Integer callProcessPayment(
        @Param("orderId") Integer orderId,
        @Param("method") String method
    );
}

