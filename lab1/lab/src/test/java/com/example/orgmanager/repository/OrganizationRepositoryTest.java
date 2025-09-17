package com.example.orgmanager.repository;

import java.util.List;
import java.util.stream.IntStream;

import com.example.orgmanager.model.Address;
import com.example.orgmanager.model.Coordinates;
import com.example.orgmanager.model.Organization;
import com.example.orgmanager.model.OrganizationType;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

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

    @Test
    void countByRatingReturnsCorrectCount() {
        organizationRepository.save(org()
                .name("A").fullName("A inc").rating(5.0).employees(10).turnover(100f)
                .build());
        organizationRepository.save(org()
                .name("B").fullName("B llc").rating(4.0).employees(5).turnover(80f)
                .build());
        organizationRepository.save(org()
                .name("C").fullName("C gmbh").rating(5.0).employees(15).turnover(120f)
                .build());

        long count = organizationRepository.countByRating(5.0);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void findByFullNameStartingWithReturnsMatches() {
        organizationRepository.save(org()
                .name("A").fullName("Alpha Corp").rating(1.0).employees(1).turnover(5f)
                .build());
        organizationRepository.save(org()
                .name("B").fullName("Beta Corp").rating(1.0).employees(1).turnover(5f)
                .build());
        organizationRepository.save(org()
                .name("C").fullName("Alpine LLC").rating(1.0).employees(1).turnover(5f)
                .build());

        List<Organization> list = organizationRepository
                .findByFullNameStartingWith("Al");
        assertThat(list).extracting(Organization::getFullName)
                .containsExactlyInAnyOrder("Alpha Corp", "Alpine LLC");
    }

    @Test
    void findTop5ByTurnoverReturnsTop5SortedDesc() {
        for (int index = 1; index <= 8; index++) {
            organizationRepository.save(org()
                    .name("N" + index)
                    .fullName("Full" + index)
                    .rating(1.0)
                    .employees(index)
                    .turnover(index * 10f)
                    .build());
        }

        List<Organization> top5 = organizationRepository
                .findTop5ByOrderByAnnualTurnoverDesc();
        List<Float> expectedTurnover = IntStream
                .iterate(8,
                        value -> value > 3,
                        value -> value - 1)
                .mapToObj(value -> Float.valueOf(value * 10f))
                .toList();

        assertThat(top5)
                .hasSize(5)
                .extracting(Organization::getAnnualTurnover)
                .containsExactlyElementsOf(expectedTurnover);
    }

    @Test
    void deleteUnassignedRemovesOrphanEntities() {
        // one org with its related entities
        organizationRepository.save(org()
                .name("A").fullName("A inc").rating(5.0).employees(10).turnover(100f)
                .build());

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
        assertThat(coordinatesRepository.findById(orphanCoords.getId()))
                .isEmpty();
    }

    private static OrgBuilder org() {
        return new OrgBuilder();
    }

    @Setter
    @Accessors(fluent = true, chain = true)
    private static final class OrgBuilder {
        private String name;
        private String fullName;
        private double rating;
        private long employees;
        private float turnover;

        Organization build() {
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
    }
}
