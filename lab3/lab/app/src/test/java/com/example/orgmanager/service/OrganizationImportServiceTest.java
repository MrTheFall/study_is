package com.example.orgmanager.service;

import com.example.orgmanager.model.Address;
import com.example.orgmanager.model.Coordinates;
import com.example.orgmanager.model.ImportJob;
import com.example.orgmanager.repository.AddressRepository;
import com.example.orgmanager.repository.CoordinatesRepository;
import com.example.orgmanager.repository.ImportJobRepository;
import com.example.orgmanager.service.dto.OrganizationForm;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationImportServiceTest {

    private static final ValidatorFactory VALIDATOR_FACTORY =
            Validation.buildDefaultValidatorFactory();

    @Mock
    private OrganizationService organizationService;
    @Mock
    private ImportJobRepository importJobRepository;
    @Mock
    private AddressRepository addressRepository;
    @Mock
    private CoordinatesRepository coordinatesRepository;

    private Validator validator;
    private OrganizationImportService importService;
    private List<ImportJob> savedJobs;

    @BeforeEach
    void setUp() {
        validator = VALIDATOR_FACTORY.getValidator();
        savedJobs = new ArrayList<>();
        when(importJobRepository.save(any(ImportJob.class))).thenAnswer(inv -> {
            ImportJob job = inv.getArgument(0);
            if (job.getId() == null) {
                job.setId(1L);
            }
            savedJobs.add(snapshot(job));
            return job;
        });
        importService = new OrganizationImportService(
                organizationService,
                importJobRepository,
                addressRepository,
                coordinatesRepository,
                validator,
                TRANSACTION_MANAGER);
    }

    @AfterAll
    static void tearDownAll() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void importFromYamlReusesNestedEntities() {
        MockMultipartFile file = testFile("import-success.yaml");

        when(coordinatesRepository.findByXAndY(anyInt(), any())).thenReturn(Optional.empty());
        when(addressRepository.findByStreetAndZip(anyString(), any())).thenReturn(Optional.empty());

        AtomicInteger organizationId = new AtomicInteger(0);
        AtomicLong coordinatesIdSeq = new AtomicLong(100);
        AtomicLong addressIdSeq = new AtomicLong(200);
        Map<Long, Coordinates> coordinatesStore = new HashMap<>();
        Map<Long, Address> addressStore = new HashMap<>();
        when(organizationService.create(any(OrganizationForm.class))).thenAnswer(inv -> {
            OrganizationForm form = inv.getArgument(0);
            var org = new com.example.orgmanager.model.Organization();
            org.setId(organizationId.incrementAndGet());

            Coordinates coordinates;
            if (form.getCoordinatesId() != null) {
                Coordinates existing = coordinatesStore.get(form.getCoordinatesId());
                if (existing != null) {
                    coordinates = cloneCoordinates(existing);
                } else {
                    coordinates = new Coordinates();
                    coordinates.setId(form.getCoordinatesId());
                    coordinates.setX(form.getCoordX());
                    coordinates.setY(form.getCoordY());
                }
            } else {
                coordinates = new Coordinates();
                coordinates.setId(coordinatesIdSeq.incrementAndGet());
                coordinates.setX(form.getCoordX());
                coordinates.setY(form.getCoordY());
                coordinatesStore.put(coordinates.getId(), cloneCoordinates(coordinates));
            }
            org.setCoordinates(coordinates);

            Address official = resolveAddress(
                    form.getOfficialAddressId(),
                    form.getOfficialStreet(),
                    form.getOfficialZipCode(),
                    addressIdSeq,
                    addressStore);
            org.setOfficialAddress(official);

            if (form.isPostalSameAsOfficial()) {
                org.setPostalAddress(official);
            } else {
                Address postal = resolveAddress(
                        form.getPostalAddressId(),
                        form.getPostalStreet(),
                        form.getPostalZipCode(),
                        addressIdSeq,
                        addressStore);
                org.setPostalAddress(postal);
            }
            return org;
        });

        ArgumentCaptor<OrganizationForm> captor = ArgumentCaptor.forClass(OrganizationForm.class);

        ImportJob job = importService.importFromYaml(file);

        assertThat(job.getStatus()).isEqualTo(com.example.orgmanager.model.ImportStatus.SUCCESS);
        assertThat(job.getImportedCount()).isEqualTo(2);
        assertThat(savedJobs)
                .extracting(ImportJob::getStatus)
                .containsExactly(
                        com.example.orgmanager.model.ImportStatus.IN_PROGRESS,
                        com.example.orgmanager.model.ImportStatus.SUCCESS);

        verify(organizationService, org.mockito.Mockito.times(2)).create(captor.capture());

        List<OrganizationForm> forms = captor.getAllValues();
        OrganizationForm first = forms.get(0);
        OrganizationForm second = forms.get(1);

        assertThat(first.getCoordinatesId()).isNull();
        assertThat(first.getCoordX()).isEqualTo(10);
        assertThat(first.getCoordY()).isEqualTo(200.0f);

        assertThat(second.getCoordinatesId()).isNotNull();
        assertThat(second.getCoordX()).isNull();
        assertThat(second.getCoordY()).isNull();

        assertThat(second.getOfficialAddressId()).isNotNull();
        assertThat(second.getOfficialStreet()).isNull();
        assertThat(second.getOfficialZipCode()).isNull();
        assertThat(second.isPostalSameAsOfficial()).isTrue();
    }

    @Test
    void importFromYamlValidationFailure() {
        MockMultipartFile file = testFile("import-invalid.yaml");

        assertThatThrownBy(() -> importService.importFromYaml(file))
                .isInstanceOf(ValidationException.class);

        assertThat(savedJobs)
                .extracting(ImportJob::getStatus)
                .containsExactly(
                        com.example.orgmanager.model.ImportStatus.IN_PROGRESS,
                        com.example.orgmanager.model.ImportStatus.FAILED);
        verify(organizationService, never()).create(any());
    }

    private ImportJob snapshot(ImportJob source) {
        ImportJob copy = new ImportJob();
        copy.setId(source.getId());
        copy.setStatus(source.getStatus());
        copy.setImportedCount(source.getImportedCount());
        copy.setErrorMessage(source.getErrorMessage());
        return copy;
    }

    private Address resolveAddress(
            Long addressId,
            String street,
            String zipCode,
            AtomicLong sequence,
            Map<Long, Address> store) {
        if (addressId != null) {
            Address existing = store.get(addressId);
            if (existing != null) {
                return cloneAddress(existing);
            }
            Address fallback = new Address();
            fallback.setId(addressId);
            fallback.setStreet(street);
            fallback.setZipCode(zipCode);
            return fallback;
        }
        Address created = new Address();
        created.setId(sequence.incrementAndGet());
        created.setStreet(street);
        created.setZipCode(zipCode);
        store.put(created.getId(), cloneAddress(created));
        return created;
    }

    private Address cloneAddress(Address source) {
        Address copy = new Address();
        copy.setId(source.getId());
        copy.setStreet(source.getStreet());
        copy.setZipCode(source.getZipCode());
        return copy;
    }

    private Coordinates cloneCoordinates(Coordinates source) {
        Coordinates copy = new Coordinates();
        copy.setId(source.getId());
        copy.setX(source.getX());
        copy.setY(source.getY());
        return copy;
    }

    private static final PlatformTransactionManager TRANSACTION_MANAGER =
            new PlatformTransactionManager() {
                @Override
                public TransactionStatus getTransaction(TransactionDefinition definition) {
                    return new SimpleTransactionStatus();
                }

                @Override
                public void commit(TransactionStatus status) {
                    // no-op
                }

                @Override
                public void rollback(TransactionStatus status) {
                    // no-op
                }
            };

    private MockMultipartFile testFile(String resourceName) {
        try (var input = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IllegalStateException("Test resource not found: " + resourceName);
            }
            return new MockMultipartFile("file", resourceName, "application/x-yaml", input.readAllBytes());
        } catch (Exception ex) {
            throw new RuntimeException("Failed to load test YAML: " + resourceName, ex);
        }
    }
}
