package com.krusty.crab.repository;

import com.krusty.crab.entity.Courier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CourierRepository extends JpaRepository<Courier, Integer> {
    Optional<Courier> findByPhone(String phone);
}

