package com.krusty.crab.repository;

import com.krusty.crab.entity.EmployeeActionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EmployeeActionLogRepository extends JpaRepository<EmployeeActionLog, Integer> {

    @Query(value = """
        select *
          from employee_action_logs l
         where (cast(:employeeId as integer) is null or l.employee_id = :employeeId)
           and (cast(:orderId as integer) is null or l.order_id = :orderId)
           and (cast(:action as varchar) is null or l.action = :action)
           and (cast(:fromTs as timestamp) is null or l.created_at >= :fromTs)
           and (cast(:toTs as timestamp) is null or l.created_at <= :toTs)
         order by l.created_at desc, l.id desc
         limit :limit
         offset :offset
        """, nativeQuery = true)
    List<EmployeeActionLog> findRecent(
        @Param("employeeId") Integer employeeId,
        @Param("orderId") Integer orderId,
        @Param("action") String action,
        @Param("fromTs") LocalDateTime fromTs,
        @Param("toTs") LocalDateTime toTs,
        @Param("limit") int limit,
        @Param("offset") int offset
    );
}
