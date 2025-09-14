package com.example.orgmanager.repository;

import com.example.orgmanager.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AddressRepository extends JpaRepository<Address, Long> {
    @org.springframework.data.jpa.repository.Modifying
    @Query("delete from Address a where not exists (select 1 from Organization o where o.officialAddress = a or o.postalAddress = a)")
    int deleteUnassigned();
}
