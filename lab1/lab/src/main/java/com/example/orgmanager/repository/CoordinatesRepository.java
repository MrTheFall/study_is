package com.example.orgmanager.repository;

import com.example.orgmanager.model.Coordinates;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface CoordinatesRepository
        extends JpaRepository<Coordinates, Long> {
    @Modifying
    @Query("delete from Coordinates c "
            + "where not exists (select 1 from Organization o "
            + "where o.coordinates = c)")
    int deleteUnassigned();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select c from Coordinates c where c.id = :id")
    Optional<Coordinates> findByIdForUpdate(@Param("id") Long id);

}
