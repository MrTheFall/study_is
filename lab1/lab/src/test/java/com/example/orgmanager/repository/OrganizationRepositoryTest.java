package com.example.orgmanager.repository;

import com.example.orgmanager.model.Address;
import com.example.orgmanager.model.Coordinates;
import com.example.orgmanager.model.Organization;
import com.example.orgmanager.model.OrganizationType;
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
        organizationRepository.save(org().name("A").fullName("Alpha Corp").rating(1.0).employees(1).turnover(5f).build());
        organizationRepository.save(org().name("B").fullName("Beta Corp").rating(1.0).employees(1).turnover(5f).build());
        organizationRepository.save(org().name("C").fullName("Alpine LLC").rating(1.0).employees(1).turnover(5f).build());

        List<Organization> list = organizationRepository.findByFullNameStartingWith("Al");
        assertThat(list).extracting(Organization::getFullName)
                .containsExactlyInAnyOrder("Alpha Corp", "Alpine LLC");
    }

    @Test
    void findTop5ByTurnoverReturnsTop5SortedDesc() {
        for (int i = 1; i <= 8; i++) {
            organizationRepository.save(
                    org().name("N" + i).fullName("Full" + i).rating(1.0).employees(i).turnover(i * 10f)
                            .build()
            );
        }

        List<Organization> top5 = organizationRepository.findTop5ByOrderByAnnualTurnoverDesc();
        assertThat(top5).hasSize(5);
        assertThat(top5).isSortedAccordingTo((o1, o2) -> Float.compare(o2.getAnnualTurnover(), o1.getAnnualTurnover()));
        assertThat(top5.get(0).getAnnualTurnover()).isEqualTo(80f);
        assertThat(top5.get(4).getAnnualTurnover()).isEqualTo(40f);
    }

    @Test
    void deleteUnassignedRemovesOrphanEntities() {
        // one org with its related entities
        organizationRepository.save(org().name("A").fullName("A inc").rating(5.0).employees(10).turnover(100f).build());

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

    private static OrgBuilder org() { return new OrgBuilder(); }

    private static class OrgBuilder {
        private String name;
        private String fullName;
        private double rating;
        private long employees;
        private float turnover;

        OrgBuilder name(String name) { this.name = name; return this; }
        OrgBuilder fullName(String fullName) { this.fullName = fullName; return this; }
        OrgBuilder rating(double rating) { this.rating = rating; return this; }
        OrgBuilder employees(long employees) { this.employees = employees; return this; }
        OrgBuilder turnover(float turnover) { this.turnover = turnover; return this; }

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
