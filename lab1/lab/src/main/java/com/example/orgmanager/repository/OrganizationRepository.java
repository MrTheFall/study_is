package com.example.orgmanager.repository;

import com.example.orgmanager.model.Organization;
import com.example.orgmanager.model.OrganizationType;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository
        extends JpaRepository<Organization, Integer> {
    Page<Organization> findByName(String name, Pageable pageable);

    Page<Organization> findByFullName(String fullName, Pageable pageable);

    Page<Organization> findByOfficialAddressStreet(
            String street,
            Pageable pageable);

    Page<Organization> findByPostalAddressStreet(
            String street,
            Pageable pageable);

    Page<Organization> findByType(OrganizationType type, Pageable pageable);

    long countByRating(double rating);

    List<Organization> findByFullNameStartingWith(String prefix);

    List<Organization> findByFullNameGreaterThan(String value);

    List<Organization> findTop5ByOrderByAnnualTurnoverDesc();

    List<Organization> findTop10ByOrderByAnnualTurnoverDesc();
}
