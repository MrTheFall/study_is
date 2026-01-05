package com.krusty.crab.repository;

import com.krusty.crab.entity.Employee;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Integer> {
    Optional<Employee> findByLogin(String login);

    boolean existsByLogin(String login);
}
