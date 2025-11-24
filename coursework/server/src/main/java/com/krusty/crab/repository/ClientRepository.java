package com.krusty.crab.repository;

import com.krusty.crab.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Integer> {
    Optional<Client> findByEmail(String email);
    Optional<Client> findByPhone(String phone);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
}

