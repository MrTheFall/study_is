package com.example.orgmanager.repository;

import com.example.orgmanager.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AddressRepository extends JpaRepository<Address, Long> {
    @Modifying
    @Query("delete from Address a "
            + "where not exists (select 1 from Organization o "
            + "where o.officialAddress = a or o.postalAddress = a)")
    int deleteUnassigned();
}
