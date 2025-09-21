package com.example.orgmanager.repository;

import com.example.orgmanager.model.Address;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface AddressRepository extends JpaRepository<Address, Long> {
    @Modifying
    @Query("delete from Address a "
            + "where not exists (select 1 from Organization o "
            + "where o.officialAddress = a or o.postalAddress = a)")
    int deleteUnassigned();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select a from Address a where a.id = :id")
    Optional<Address> findByIdForUpdate(@Param("id") Long id);

}
