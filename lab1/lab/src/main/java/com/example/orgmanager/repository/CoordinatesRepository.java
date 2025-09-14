package com.example.orgmanager.repository;

import com.example.orgmanager.model.Coordinates;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CoordinatesRepository extends JpaRepository<Coordinates, Long> {
    @org.springframework.data.jpa.repository.Modifying
    @Query("delete from Coordinates c where not exists (select 1 from Organization o where o.coordinates = c)")
    int deleteUnassigned();
}
