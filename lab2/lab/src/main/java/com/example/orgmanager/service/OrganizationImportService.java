package com.example.orgmanager.service;

import com.example.orgmanager.model.Address;
import com.example.orgmanager.model.Coordinates;
import com.example.orgmanager.model.ImportJob;
import com.example.orgmanager.model.ImportStatus;
import com.example.orgmanager.model.Organization;
import com.example.orgmanager.model.OrganizationType;
import com.example.orgmanager.repository.AddressRepository;
import com.example.orgmanager.repository.CoordinatesRepository;
import com.example.orgmanager.repository.ImportJobRepository;
import com.example.orgmanager.web.dto.OrganizationForm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OrganizationImportService {
    private static final String POSTAL_ADDRESS_REQUIRED =
            "Запись #%d: требуется указать почтовый адрес или установить флаг postalSameAsOfficial";

    private final OrganizationService organizationService;
    private final ImportJobRepository importJobRepository;
    private final AddressRepository addressRepository;
    private final CoordinatesRepository coordinatesRepository;
    private final Validator validator;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper yamlMapper;

    public OrganizationImportService(OrganizationService organizationService,
            ImportJobRepository importJobRepository,
            AddressRepository addressRepository,
            CoordinatesRepository coordinatesRepository,
            Validator validator,
            PlatformTransactionManager transactionManager) {
        this.organizationService = organizationService;
        this.importJobRepository = importJobRepository;
        this.addressRepository = addressRepository;
        this.coordinatesRepository = coordinatesRepository;
        this.validator = validator;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.findAndRegisterModules();
    }

    public ImportJob importFromYaml(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Импортируемый файл не может быть пустым");
        }

        ImportJob job = new ImportJob();
        job.setStatus(ImportStatus.IN_PROGRESS);
        job = importJobRepository.save(job);

        try {
            OrganizationImportPayload payload = readPayload(file);
            validatePayload(payload);
            List<OrganizationImportItem> items = payload.getOrganizations();
            transactionTemplate.executeWithoutResult(status -> {
                Map<CoordinateKey, Long> coordinatesCache = new HashMap<>();
                Map<AddressKey, Long> addressCache = new HashMap<>();
                for (OrganizationImportItem item : items) {
                    CoordinatesImport coords = item.getCoordinates();
                    CoordinateKey key = new CoordinateKey(coords.getX(), coords.getY());
                    Long coordinatesId = coordinatesCache.get(key);
                    if (coordinatesId == null) {
                        coordinatesId = lookupExistingCoordinatesId(key);
                        if (coordinatesId != null) {
                            coordinatesCache.put(key, coordinatesId);
                        }
                    }
                    OrganizationForm form = toForm(item, coordinatesId, addressCache);
                    Organization saved = organizationService.create(form);
                    if (coordinatesId == null && saved.getCoordinates() != null) {
                        coordinatesCache.putIfAbsent(key, saved.getCoordinates().getId());
                    }
                    if (saved.getOfficialAddress() != null) {
                        AddressKey officialKey = buildAddressKey(saved.getOfficialAddress());
                        addressCache.putIfAbsent(officialKey, saved.getOfficialAddress().getId());
                    }
                    if (saved.getPostalAddress() != null) {
                        AddressKey postalKey = buildAddressKey(saved.getPostalAddress());
                        addressCache.putIfAbsent(postalKey, saved.getPostalAddress().getId());
                    }
                }
            });
            job.setStatus(ImportStatus.SUCCESS);
            job.setImportedCount(items.size());
            job.setErrorMessage(null);
            return importJobRepository.save(job);
        } catch (ValidationException ex) {
            job.setStatus(ImportStatus.FAILED);
            job.setErrorMessage(ex.getMessage());
            importJobRepository.save(job);
            throw ex;
        } catch (RuntimeException ex) {
            job.setStatus(ImportStatus.FAILED);
            job.setErrorMessage(ex.getMessage());
            importJobRepository.save(job);
            throw ex;
        }
    }

    public List<ImportJob> history() {
        return importJobRepository.findAllByOrderByCreatedAtDesc();
    }

    private OrganizationImportPayload readPayload(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            OrganizationImportPayload payload =
                    yamlMapper.readValue(input, OrganizationImportPayload.class);
            if (payload == null) {
                throw new ValidationException("Файл импорта пуст");
            }
            return payload;
        } catch (IOException ex) {
            throw new ValidationException("Не удалось прочитать YAML-файл: " + ex.getMessage());
        }
    }

    private void validatePayload(OrganizationImportPayload payload) {
        Set<ConstraintViolation<OrganizationImportPayload>> payloadViolations =
                validator.validate(payload);
        if (!payloadViolations.isEmpty()) {
            throw new ValidationException(formatViolations(payloadViolations));
        }

        List<OrganizationImportItem> items = payload.getOrganizations();
        for (int i = 0; i < items.size(); i++) {
            OrganizationImportItem item = items.get(i);
            Set<ConstraintViolation<OrganizationImportItem>> itemViolations =
                    validator.validate(item);
            if (!itemViolations.isEmpty()) {
                String prefix = "Запись #" + (i + 1) + ": ";
                throw new ValidationException(prefix + formatViolations(itemViolations));
            }
            if (!item.postalAddressProvided()) {
                throw new ValidationException(POSTAL_ADDRESS_REQUIRED.formatted(i + 1));
            }
        }
    }

    private OrganizationForm toForm(
            OrganizationImportItem item,
            Long coordinatesId,
            Map<AddressKey, Long> addressCache) {
        OrganizationForm form = new OrganizationForm();
        form.setName(item.getName());
        form.setFullName(item.getFullName());
        form.setType(item.getType());
        form.setAnnualTurnover(item.getAnnualTurnover().floatValue());
        form.setEmployeesCount(item.getEmployeesCount());
        form.setRating(item.getRating());

        CoordinatesImport coords = item.getCoordinates();
        if (coordinatesId != null) {
            form.setCoordinatesId(coordinatesId);
            form.setCoordX(null);
            form.setCoordY(null);
        } else {
            form.setCoordX(coords.getX());
            form.setCoordY(coords.getY());
        }

        AddressImport official = item.getOfficialAddress();
        AddressKey officialKey = buildAddressKey(official);
        Long officialAddressId = addressCache.computeIfAbsent(
                officialKey,
                key -> lookupExistingAddressId(key));
        if (officialAddressId != null) {
            form.setOfficialAddressId(officialAddressId);
        } else {
            form.setOfficialStreet(officialKey.street());
            form.setOfficialZipCode(officialKey.zip());
        }

        if (item.isPostalSameAsOfficial()) {
            form.setPostalSameAsOfficial(true);
        } else {
            AddressImport postal = Objects.requireNonNull(
                    item.getPostalAddress(),
                    "Почтовый адрес должен быть указан");
            AddressKey postalKey = buildAddressKey(postal);
            Long postalAddressId = addressCache.computeIfAbsent(
                    postalKey,
                    key -> lookupExistingAddressId(key));
            if (postalAddressId != null) {
                form.setPostalAddressId(postalAddressId);
            } else {
                form.setPostalStreet(postalKey.street());
                form.setPostalZipCode(postalKey.zip());
            }
        }
        return form;
    }

    private Long lookupExistingCoordinatesId(CoordinateKey key) {
        return coordinatesRepository.findByXAndY(key.x(), key.y())
                .map(Coordinates::getId)
                .orElse(null);
    }

    private Long lookupExistingAddressId(AddressKey key) {
        if (key == null) {
            return null;
        }
        return addressRepository.findByStreetAndZip(
                key.street(),
                key.zip())
                .map(Address::getId)
                .orElse(null);
    }

    private String formatViolations(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
    }

    @Getter
    @Setter
    @NoArgsConstructor
    private static final class OrganizationImportPayload {
        @NotEmpty(message = "Список организаций не может быть пустым")
        @Valid
        private List<OrganizationImportItem> organizations = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    private static final class OrganizationImportItem {
        @NotBlank
        private String name;

        @NotBlank
        private String fullName;

        private OrganizationType type;

        @NotNull
        @Positive
        private Double annualTurnover;

        @NotNull
        @Positive
        private Long employeesCount;

        @NotNull
        @Positive
        private Double rating;

        @NotNull
        @Valid
        private CoordinatesImport coordinates;

        @NotNull
        @Valid
        private AddressImport officialAddress;

        @Valid
        private AddressImport postalAddress;

        private boolean postalSameAsOfficial;

        boolean postalAddressProvided() {
            return postalSameAsOfficial || postalAddress != null;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    private static final class CoordinatesImport {
        private static final long MIN_X = -523L;

        @NotNull
        @Min(MIN_X)
        private Integer x;

        @NotNull
        @DecimalMax(value = "476")
        private Float y;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    private static final class AddressImport {
        @NotBlank
        private String street;

        private String zipCode;
    }

    private record CoordinateKey(int x, Float y) { }

    private AddressKey buildAddressKey(AddressImport address) {
        return new AddressKey(
                normalizeStreet(address.getStreet()),
                normalizeZip(address.getZipCode()));
    }

    private AddressKey buildAddressKey(Address address) {
        return new AddressKey(
                normalizeStreet(address.getStreet()),
                normalizeZip(address.getZipCode()));
    }

    private String normalizeStreet(String street) {
        return street == null ? null : street.trim();
    }

    private String normalizeZip(String zip) {
        if (zip == null) {
            return null;
        }
        String trimmed = zip.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record AddressKey(String street, String zip) { }
}
