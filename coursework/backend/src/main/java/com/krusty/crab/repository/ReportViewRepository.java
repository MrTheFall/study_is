package com.krusty.crab.repository;

import com.krusty.crab.entity.ReportView;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportViewRepository extends JpaRepository<ReportView, Integer> {

    @Query(
            value =
                    """
        select *
          from report_views rv
         order by rv.viewed_at desc
         limit :limit
         offset :offset
        """,
            nativeQuery = true)
    List<ReportView> findRecent(@Param("limit") int limit, @Param("offset") int offset);
}
