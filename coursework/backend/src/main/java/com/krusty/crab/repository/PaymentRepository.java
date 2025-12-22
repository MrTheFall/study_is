package com.krusty.crab.repository;

import com.krusty.crab.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {

    Optional<Payment> findByOrderId(Integer orderId);

    boolean existsByOrderId(Integer orderId);

    @Query(value = """
        select p.*
          from payments p
          join orders o on o.id = p.order_id
         where (cast(:clientId as integer) is null or o.client_id = :clientId)
           and (cast(:success as boolean) is null or p.success = :success)
           and (cast(:fromTs as timestamp) is null or p.paid_at >= :fromTs)
           and (cast(:toTs as timestamp) is null or p.paid_at <= :toTs)
         order by p.paid_at desc nulls last, p.id desc
         limit :limit
         offset :offset
        """, nativeQuery = true)
    List<Payment> findRecent(
        @Param("clientId") Integer clientId,
        @Param("success") Boolean success,
        @Param("fromTs") LocalDateTime fromTs,
        @Param("toTs") LocalDateTime toTs,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    @Query(value = "SELECT process_payment(:orderId, :method)", nativeQuery = true)
    Integer callProcessPayment(
        @Param("orderId") Integer orderId,
        @Param("method") String method
    );
}
