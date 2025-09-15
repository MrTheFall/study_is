package com.example.orgmanager.repository;

import com.example.orgmanager.model.Address;
import com.example.orgmanager.model.Coordinates;
import com.example.orgmanager.model.Organization;
import com.example.orgmanager.model.OrganizationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class OrganizationRepositoryTest {

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private CoordinatesRepository coordinatesRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Organization newOrg(String name, String fullName, double rating, long employees, float turnover) {
        Organization o = new Organization();
        o.setName(name);
        o.setFullName(fullName);
        o.setRating(rating);
        o.setEmployeesCount(employees);
        o.setAnnualTurnover(turnover);
        o.setType(OrganizationType.COMMERCIAL);

        Coordinates c = new Coordinates();
        c.setX(10);
        c.setY(20f);
        o.setCoordinates(c);

        Address a1 = new Address();
        a1.setStreet("Main");
        a1.setZipCode("1000");
        o.setOfficialAddress(a1);

        Address a2 = new Address();
        a2.setStreet("Second");
        a2.setZipCode("2000");
        o.setPostalAddress(a2);

        return o;
    }

    @Test
    @DisplayName("countByRating returns correct count")
    void countByRating() {
        organizationRepository.save(newOrg("A", "A inc", 5.0, 10, 100f));
        organizationRepository.save(newOrg("B", "B llc", 4.0, 5, 80f));
        organizationRepository.save(newOrg("C", "C gmbh", 5.0, 15, 120f));

        long count = organizationRepository.countByRating(5.0);
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("findByFullNameStartingWith filters correctly")
    void startsWith() {
        organizationRepository.save(newOrg("A", "Alpha Corp", 1.0, 1, 5f));
        organizationRepository.save(newOrg("B", "Beta Corp", 1.0, 1, 5f));
        organizationRepository.save(newOrg("C", "Alpine LLC", 1.0, 1, 5f));

        List<Organization> list = organizationRepository.findByFullNameStartingWith("Al");
        assertThat(list).extracting(Organization::getFullName)
                .containsExactlyInAnyOrder("Alpha Corp", "Alpine LLC");
    }

    @Test
    @DisplayName("top5ByTurnover returns top 5 sorted desc")
    void top5ByTurnover() {
        for (int i = 1; i <= 8; i++) {
            organizationRepository.save(newOrg("N" + i, "Full" + i, 1.0, i, i * 10f));
        }

        List<Organization> top5 = organizationRepository.findTop5ByOrderByAnnualTurnoverDesc();
        assertThat(top5).hasSize(5);
        assertThat(top5).isSortedAccordingTo((o1, o2) -> Float.compare(o2.getAnnualTurnover(), o1.getAnnualTurnover()));
        assertThat(top5.get(0).getAnnualTurnover()).isEqualTo(80f);
        assertThat(top5.get(4).getAnnualTurnover()).isEqualTo(40f);
    }

    @Test
    @DisplayName("deleteUnassigned removes addresses and coords not referenced")
    void deleteUnassigned() {
        // one org with its related entities
        organizationRepository.save(newOrg("A", "A inc", 5.0, 10, 100f));

        // unassigned address and coordinates
        Address orphanAddress = new Address();
        orphanAddress.setStreet("Orphan");
        orphanAddress.setZipCode(null);
        addressRepository.save(orphanAddress);

        Coordinates orphanCoords = new Coordinates();
        orphanCoords.setX(1);
        orphanCoords.setY(2f);
        coordinatesRepository.save(orphanCoords);

        int removedAddr = addressRepository.deleteUnassigned();
        int removedCoords = coordinatesRepository.deleteUnassigned();

        entityManager.clear();

        assertThat(removedAddr).isGreaterThanOrEqualTo(1);
        assertThat(removedCoords).isGreaterThanOrEqualTo(1);

        assertThat(addressRepository.findById(orphanAddress.getId())).isEmpty();
        assertThat(coordinatesRepository.findById(orphanCoords.getId())).isEmpty();
    }
}
