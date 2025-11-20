package com.example.orgmanager.service;

import com.example.orgmanager.model.Address;
import com.example.orgmanager.model.Organization;
import com.example.orgmanager.model.OrganizationType;
import com.example.orgmanager.repository.AddressRepository;
import com.example.orgmanager.repository.CoordinatesRepository;
import com.example.orgmanager.repository.OrganizationRepository;
import com.example.orgmanager.web.dto.OrganizationForm;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OrganizationServiceCleanupRaceIT.TestConfig.class)
class OrganizationServiceCleanupRaceIT {
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
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQL95Dialect");
    }

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private CoordinatesRepository coordinatesRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Organization targetOrg;
    private Address orphanAddress;

    @BeforeEach
    void setUp() {
        organizationRepository.deleteAll();
        addressRepository.deleteAll();
        coordinatesRepository.deleteAll();

        targetOrg = organizationService.create(buildForm(
                "Target",
                10,
                10f,
                "Official",
                "00000",
                "Postal",
                "11111"));

        orphanAddress = new Address();
        orphanAddress.setStreet("Orphan");
        orphanAddress.setZipCode("22222");
        orphanAddress = addressRepository.save(orphanAddress);
    }

    private static OrganizationForm buildForm(String nameSuffix,
            int coordX,
            float coordY,
            String officialStreet,
            String officialZip,
            String postalStreet,
            String postalZip) {
        OrganizationForm form = new OrganizationForm();
        form.setName("Org " + nameSuffix);
        form.setFullName("Organization " + nameSuffix);
        form.setType(OrganizationType.PUBLIC);
        form.setAnnualTurnover(1.0f);
        form.setEmployeesCount(10L);
        form.setRating(5.0);
        form.setCoordX(coordX);
        form.setCoordY(coordY);
        form.setOfficialStreet(officialStreet);
        form.setOfficialZipCode(officialZip);
        form.setPostalStreet(postalStreet);
        form.setPostalZipCode(postalZip);
        form.setPostalSameAsOfficial(false);
        return form;
    }

    @Autowired
    private BlockingAddressAspect blockingAspect;

    @Test
    @Timeout(value = 60)
    void cleanupCompetesWithUpdateAndRemovesTargetAddress() throws Exception {
        CountDownLatch saveBlocked = new CountDownLatch(1);
        CountDownLatch cleanupFinished = new CountDownLatch(1);

        blockingAspect.configure(orphanAddress.getId(), saveBlocked, cleanupFinished);

        OrganizationForm form = new OrganizationForm();
        form.setId(targetOrg.getId());
        form.setName(targetOrg.getName());
        form.setFullName(targetOrg.getFullName());
        form.setType(targetOrg.getType());
        form.setAnnualTurnover(targetOrg.getAnnualTurnover());
        form.setEmployeesCount(targetOrg.getEmployeesCount());
        form.setRating(targetOrg.getRating());
        form.setCoordinatesId(targetOrg.getCoordinates().getId());
        form.setOfficialAddressId(orphanAddress.getId());
        form.setPostalSameAsOfficial(true);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Throwable> updateResult;
        try {
            updateResult = executor.submit(() -> {
                try {
                    organizationService.update(targetOrg.getId(), form);
                    return null;
                } catch (Throwable ex) {
                    return ex;
                }
            });

            // wait until update thread attempts to save the orphan address
            saveBlocked.await(30, TimeUnit.SECONDS);

            // trigger cleanup via separate delete that schedules orphan cleanup
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            try {
                template.executeWithoutResult(status -> {
                    addressRepository.deleteUnassigned();
                    coordinatesRepository.deleteUnassigned();
                });
            } catch (DataIntegrityViolationException ignored) {
                // cleanup raced with update and observed a referenced row, production code ignores it
            }
            cleanupFinished.countDown();

            Throwable failure = updateResult.get(FUTURE_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertThat(failure)
                    .as("update should succeed even if cleanup runs in parallel")
                    .isNull();

            Organization refreshed = organizationRepository.findById(targetOrg.getId()).orElseThrow();
            assertThat(refreshed.getOfficialAddress().getId()).isEqualTo(orphanAddress.getId());
            assertThat(addressRepository.findById(orphanAddress.getId())).isPresent();
        } catch (ExecutionException e) {
            throw e;
        } finally {
            executor.shutdownNow();
            blockingAspect.clear();
        }

        assertThat(addressRepository.findById(orphanAddress.getId())).isPresent();
    }

    @TestConfiguration
    @EnableAspectJAutoProxy
    static class TestConfig {
        @Bean
        BlockingAddressAspect blockingAddressAspect() {
            return new BlockingAddressAspect();
        }
    }

    @Aspect
    static class BlockingAddressAspect {
        private volatile Long targetAddressId;
        private CountDownLatch saveBlocked;
        private CountDownLatch cleanupFinished;

        void configure(Long targetId, CountDownLatch saveBlocked, CountDownLatch cleanupFinished) {
            this.targetAddressId = targetId;
            this.saveBlocked = saveBlocked;
            this.cleanupFinished = cleanupFinished;
        }

        void clear() {
            targetAddressId = null;
            saveBlocked = null;
            cleanupFinished = null;
        }

        @Around("execution(* com.example.orgmanager.repository.AddressRepository.save(..)) && args(address)")
        public Object aroundSave(ProceedingJoinPoint pjp, Address address) throws Throwable {
            if (targetAddressId != null
                    && address != null
                    && address.getId() != null
                    && address.getId().equals(targetAddressId)
                    && saveBlocked != null
                    && cleanupFinished != null) {
                saveBlocked.countDown();
                cleanupFinished.await(30, TimeUnit.SECONDS);
            }
            return pjp.proceed();
        }
    }
}
