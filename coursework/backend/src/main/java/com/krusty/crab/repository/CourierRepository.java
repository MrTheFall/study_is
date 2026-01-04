package com.krusty.crab.repository;

import com.krusty.crab.entity.Courier;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourierRepository extends JpaRepository<Courier, Integer> {
    Optional<Courier> findByPhone(String phone);
}
