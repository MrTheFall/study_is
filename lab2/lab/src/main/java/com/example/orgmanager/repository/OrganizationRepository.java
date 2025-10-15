package com.example.orgmanager.repository;

import com.example.orgmanager.model.Organization;
import com.example.orgmanager.model.OrganizationType;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    Stream<Organization> streamByFullNameStartingWith(String prefix);

    Stream<Organization> streamByFullNameGreaterThan(String value);

    List<Organization> findTop5ByOrderByAnnualTurnoverDesc();

    Stream<Organization> streamTop10ByOrderByAnnualTurnoverDesc();

    @Query("select o.id as id, o.name as name, o.fullName as fullName "
            + "from Organization o "
            + "where (:excludeId is null or o.id <> :excludeId)")
    List<NameProjection> findNamesExcludingId(@Param("excludeId") Integer excludeId);

    interface NameProjection {
        Integer getId();

        String getName();

        String getFullName();
    }
}
