package com.krusty.crab.repository;

import com.krusty.crab.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Integer> {
    List<MenuItem> findByAvailableTrue();
    List<MenuItem> findByNameContainingIgnoreCase(String name);
}

