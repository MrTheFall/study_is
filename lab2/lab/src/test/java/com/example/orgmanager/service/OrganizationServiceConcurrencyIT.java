package com.example.orgmanager.service;

import com.example.orgmanager.model.Address;
import com.example.orgmanager.model.Coordinates;
import com.example.orgmanager.model.Organization;
import com.example.orgmanager.model.OrganizationType;
import com.example.orgmanager.repository.AddressRepository;
import com.example.orgmanager.repository.CoordinatesRepository;
import com.example.orgmanager.repository.OrganizationRepository;
import com.example.orgmanager.web.dto.OrganizationForm;
import java.time.Duration;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrganizationServiceConcurrencyIT {

    private static final int STRESS_RUNS = 3;
    private static final Duration FUTURE_TIMEOUT = Duration.ofSeconds(30);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("orgmgr_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQL95Dialect");
    }

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private CoordinatesRepository coordinatesRepository;

    private Organization orgA;
    private Organization orgB;

    @BeforeEach
    void setUp() {
        orgA = createOrganization("Atlas", 10, 10f, "Address 1");
        orgB = createOrganization("Zephyr", 20, 20f, "Address 2");
    }

    @AfterAll
    void tearDownAll() {
        POSTGRES.stop();
    }

    @Test
    void concurrentUpdatesSwapReferencesDoNotLoseRelations() throws Exception {
        for (int i = 0; i < STRESS_RUNS; i++) {
            Organization currentA = organizationRepository.findById(orgA.getId()).orElseThrow();
            Organization currentB = organizationRepository.findById(orgB.getId()).orElseThrow();

            Coordinates coordsForA = currentA.getCoordinates();
            Coordinates coordsForB = currentB.getCoordinates();
            Address addressForA = currentA.getOfficialAddress();
            Address addressForB = currentB.getOfficialAddress();

            OrganizationForm formForA = buildSwapForm(currentA, coordsForB, addressForB);
            OrganizationForm formForB = buildSwapForm(currentB, coordsForA, addressForA);

            runConcurrently(() -> organizationService.update(currentA.getId(), formForA),
                    () -> organizationService.update(currentB.getId(), formForB));
        }

        Organization reloadedA = organizationRepository.findById(orgA.getId()).orElseThrow();
        Organization reloadedB = organizationRepository.findById(orgB.getId()).orElseThrow();

        assertThat(reloadedA.getOfficialAddress()).isNotNull();
        assertThat(reloadedA.getCoordinates()).isNotNull();
        assertThat(reloadedB.getOfficialAddress()).isNotNull();
        assertThat(reloadedB.getCoordinates()).isNotNull();

        assertThat(addressRepository.count()).isEqualTo(2);
        assertThat(coordinatesRepository.count()).isEqualTo(2);
    }

    private void runConcurrently(Runnable first, Runnable second)
            throws InterruptedException, ExecutionException, TimeoutException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            Future<?> future1 = executor.submit(() -> runWithBarrier(first, barrier));
            Future<?> future2 = executor.submit(() -> runWithBarrier(second, barrier));
            future1.get(FUTURE_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            future2.get(FUTURE_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private void runWithBarrier(Runnable task, CyclicBarrier barrier) {
        try {
            barrier.await();
            task.run();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private OrganizationForm buildSwapForm(
            Organization source,
            Coordinates targetCoordinates,
            Address targetAddress) {
        OrganizationForm form = new OrganizationForm();
        form.setId(source.getId());
        form.setName(source.getName());
        form.setFullName(source.getFullName());
        form.setType(source.getType());
        form.setAnnualTurnover(source.getAnnualTurnover());
        form.setEmployeesCount(source.getEmployeesCount());
        form.setRating(source.getRating());
        form.setCoordinatesId(targetCoordinates.getId());
        form.setOfficialAddressId(targetAddress.getId());
        form.setPostalSameAsOfficial(true);
        return form;
    }

    private Organization createOrganization(
            String suffix,
            int coordX,
            float coordY,
            String street) {
        Coordinates coordinates = new Coordinates();
        coordinates.setX(coordX);
        coordinates.setY(coordY);

        Address address = new Address();
        address.setStreet(street);
        address.setZipCode("100000");

        Organization org = new Organization();
        org.setName("ООО Организация " + suffix);
        org.setFullName("ООО Организация Полное " + suffix);
        org.setType(OrganizationType.COMMERCIAL);
        org.setAnnualTurnover(200_000f);
        org.setEmployeesCount(100L);
        org.setRating(10.0d);
        org.setCoordinates(coordinates);
        org.setOfficialAddress(address);
        org.setPostalAddress(address);
        return organizationRepository.save(org);
    }
}
