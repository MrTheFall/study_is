package com.example.orgmanager.repository;

import com.example.orgmanager.model.AppLock;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface AppLockRepository extends JpaRepository<AppLock, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select l from AppLock l where l.name = :name")
    Optional<AppLock> findByNameForUpdate(@Param("name") String name);

    @Modifying
    @Query(value = "insert into app_lock(name) values (:name) on conflict (name) do nothing", nativeQuery = true)
    void insertIgnore(@Param("name") String name);
}
