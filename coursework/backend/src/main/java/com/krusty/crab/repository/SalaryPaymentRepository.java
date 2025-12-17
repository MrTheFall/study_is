package com.krusty.crab.repository;

import com.krusty.crab.entity.SalaryPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SalaryPaymentRepository extends JpaRepository<SalaryPayment, Integer> {

    @Query(value = """
        select *
          from salary_payments sp
         where (:employeeId is null or sp.employee_id = :employeeId)
           and (:fromTs is null or sp.paid_at >= :fromTs)
           and (:toTs is null or sp.paid_at <= :toTs)
         order by sp.paid_at desc
         limit :limit
         offset :offset
        """, nativeQuery = true)
    List<SalaryPayment> findRecent(
        @Param("employeeId") Integer employeeId,
        @Param("fromTs") LocalDateTime fromTs,
        @Param("toTs") LocalDateTime toTs,
        @Param("limit") int limit,
        @Param("offset") int offset
    );
}

