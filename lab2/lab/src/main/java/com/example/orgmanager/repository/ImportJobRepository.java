package com.example.orgmanager.repository;

import com.example.orgmanager.model.ImportJob;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {
    List<ImportJob> findAllByOrderByCreatedAtDesc();
}
