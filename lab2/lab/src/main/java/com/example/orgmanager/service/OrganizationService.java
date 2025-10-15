package com.example.orgmanager.service;

import com.example.orgmanager.model.Address;
import com.example.orgmanager.model.Coordinates;
import com.example.orgmanager.model.Organization;
import com.example.orgmanager.model.OrganizationType;
import com.example.orgmanager.repository.AddressRepository;
import com.example.orgmanager.repository.CoordinatesRepository;
import com.example.orgmanager.repository.OrganizationRepository;
import com.example.orgmanager.service.lock.DatabaseLockService;
import com.example.orgmanager.web.dto.OrganizationForm;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrganizationService {
    private static final String ORGANIZATION_NOT_FOUND = "Organization not found";
    private static final String COORDINATES_NOT_FOUND = "Выбранные координаты не найдены";
    private static final String COORDINATES_REQUIRED =
            "Требуется указать координаты X и Y, если не выбран существующий вариант";
    private static final String OFFICIAL_ADDRESS_NOT_FOUND =
            "Выбранный официальный адрес не найден";
    private static final String OFFICIAL_ADDRESS_REQUIRED =
            "Требуется указать улицу официального адреса, если не выбран существующий";
    private static final String POSTAL_ADDRESS_NOT_FOUND =
            "Выбранный почтовый адрес не найден";
    private static final String POSTAL_ADDRESS_REQUIRED =
            "Требуется указать улицу почтового адреса, если не выбран существующий";
    private static final String NAME_ALREADY_EXISTS =
            "Организация с именем '%s' уже существует";
    private static final String NAME_LOCK_BUSY =
            "Название '%s' сейчас редактируется другим пользователем. Повторите попытку позже.";
    private static final String CONCURRENT_MODIFICATION =
            "Операция не выполнена из-за одновременных изменений. Повторите попытку.";
    private static final int ANALYTICS_STREAM_LIMIT = 200;
    private static final double MIN_COORDINATE_DISTANCE = 1d;
    private static final double MIN_COORDINATE_DISTANCE_SQUARED =
            MIN_COORDINATE_DISTANCE * MIN_COORDINATE_DISTANCE;
    private static final int SIMILAR_NAME_DISTANCE_THRESHOLD = 3;

    private static final Map<OrganizationType, LongRange> EMPLOYEE_CONSTRAINTS = Map.of(
            OrganizationType.COMMERCIAL, new LongRange(20L, 5000L),
            OrganizationType.PUBLIC, new LongRange(100L, 15000L),
            OrganizationType.TRUST, new LongRange(5L, 1500L),
            OrganizationType.PRIVATE_LIMITED_COMPANY, new LongRange(1L, 500L));

    private static final Map<OrganizationType, FloatRange> TURNOVER_CONSTRAINTS = Map.of(
            OrganizationType.COMMERCIAL, new FloatRange(100_000f, 50_000_000f),
            OrganizationType.PUBLIC, new FloatRange(1_000_000f, 100_000_000f),
            OrganizationType.TRUST, new FloatRange(50_000f, 10_000_000f),
            OrganizationType.PRIVATE_LIMITED_COMPANY, new FloatRange(10_000f, 5_000_000f));

    private static final Map<OrganizationType, String> TYPE_NAME_PREFIXES = Map.of(
            OrganizationType.COMMERCIAL, "ООО",
            OrganizationType.PUBLIC, "ПАО",
            OrganizationType.TRUST, "ТР",
            OrganizationType.PRIVATE_LIMITED_COMPANY, "ИП");

    private final OrganizationRepository organizationRepository;
    private final AddressRepository addressRepository;
    private final CoordinatesRepository coordinatesRepository;
    private final OrganizationEventPublisher eventPublisher;
    private final TransactionTemplate cleanupTransaction;
    private static final String ORPHAN_CLEANUP_LOCK = "orphan_cleanup";
    private final DatabaseLockService databaseLockService;

    public OrganizationService(OrganizationRepository organizationRepository,
            AddressRepository addressRepository,
            CoordinatesRepository coordinatesRepository,
            OrganizationEventPublisher eventPublisher,
            PlatformTransactionManager transactionManager,
            DatabaseLockService databaseLockService) {
        this.organizationRepository = organizationRepository;
        this.addressRepository = addressRepository;
        this.coordinatesRepository = coordinatesRepository;
        this.eventPublisher = eventPublisher;
        this.cleanupTransaction = new TransactionTemplate(transactionManager);
        this.cleanupTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.databaseLockService = databaseLockService;
    }

    public Page<Organization> list(
            OrganizationFilter filter,
            Pageable pageable) {
        Objects.requireNonNull(filter, "filter");
        if (filter.isEmpty()) {
            return organizationRepository.findAll(pageable);
        }
        String field = filter.field().orElseThrow();
        String value = filter.value().orElseThrow();
        return switch (field) {
            case "name" -> organizationRepository.findByName(value, pageable);
            case "fullName" -> organizationRepository.findByFullName(value, pageable);
            case "officialStreet" ->
                    organizationRepository.findByOfficialAddressStreet(value, pageable);
            case "postalStreet" ->
                    organizationRepository.findByPostalAddressStreet(value, pageable);
            case "type" -> {
                OrganizationType type;
                try {
                    type = OrganizationType.valueOf(value);
                } catch (IllegalArgumentException ex) {
                    throw new ValidationException("Неизвестный тип организации: " + value);
                }
                yield organizationRepository.findByType(type, pageable);
            }
            default -> organizationRepository.findAll(pageable);
        };
    }

    public Optional<Organization> findById(Integer id) {
        return organizationRepository.findById(id);
    }

    public List<Address> allAddresses() {
        return addressRepository.findAll();
    }

    public List<Coordinates> allCoordinates() {
        return coordinatesRepository.findAll();
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Organization create(OrganizationForm form) {
        String targetName = form.getName();
        String normalizedName = normalizeName(targetName);
        acquireNameLock(targetName, normalizedName);
        ensureUniqueName(targetName, null);

        Organization org = new Organization();
        try {
            applyForm(org, form);
            validateBusinessRules(org, null);
            Organization saved = organizationRepository.save(org);
            organizationRepository.flush();
            afterCommit(() -> eventPublisher.broadcast("created", saved.getId()));
            return saved;
        } catch (DataIntegrityViolationException ex) {
            throw new ValidationException(NAME_ALREADY_EXISTS.formatted(targetName));
        } catch (ConcurrencyFailureException ex) {
            throw new ValidationException(CONCURRENT_MODIFICATION);
        }
    }

    @Transactional
    public Organization update(Integer id, OrganizationForm form) {
        Organization org = organizationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        ORGANIZATION_NOT_FOUND));
        String targetName = form.getName();
        String normalizedName = normalizeName(targetName);
        String currentNormalizedName = normalizeName(org.getName());
        if (!normalizedName.equals(currentNormalizedName)) {
            acquireNameLock(targetName, normalizedName);
        }
        ensureUniqueName(targetName, id);

        try {
            applyForm(org, form);
            validateBusinessRules(org, id);
            Organization saved = organizationRepository.save(org);
            organizationRepository.flush();
            scheduleOrphanCleanup();
            afterCommit(() -> eventPublisher.broadcast("updated", saved.getId()));
            return saved;
        } catch (DataIntegrityViolationException ex) {
            throw new ValidationException(NAME_ALREADY_EXISTS.formatted(targetName));
        } catch (ConcurrencyFailureException ex) {
            throw new ValidationException(CONCURRENT_MODIFICATION);
        }
    }

    @Transactional
    public void delete(Integer id) {
        Organization org = organizationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        ORGANIZATION_NOT_FOUND));
        organizationRepository.delete(org);
        organizationRepository.flush();
        scheduleOrphanCleanup();
        afterCommit(() -> eventPublisher.broadcast("deleted", id));
    }

    private void validateBusinessRules(Organization org, Integer currentId) {
        validateCoordinateDistance(org.getCoordinates());
        validateNameSimilarity(org, currentId);
        validateTypeSpecificConstraints(org);
    }

    private void validateCoordinateDistance(Coordinates coordinates) {
        if (coordinates == null) {
            return;
        }
        long nearby = coordinatesRepository.countWithinDistance(
                coordinates.getX(),
                coordinates.getY(),
                MIN_COORDINATE_DISTANCE_SQUARED,
                coordinates.getId());
        if (nearby > 0) {
            throw new ValidationException(
                    "Координаты указанного адреса находятся ближе чем на 1 у.е. "
                            + "к существующему адресу. Выберите другое расположение.");
        }
    }

    private void validateNameSimilarity(Organization org, Integer currentId) {
        List<NameCandidate> newNames = collectNameCandidates(
                org.getName(),
                org.getFullName());
        if (newNames.isEmpty()) {
            return;
        }
        List<OrganizationRepository.NameProjection> projections =
                organizationRepository.findNamesExcludingId(currentId);
        if (projections == null || projections.isEmpty()) {
            return;
        }
        List<NameCandidate> existingNames = new ArrayList<>();
        for (OrganizationRepository.NameProjection projection : projections) {
            existingNames.addAll(collectNameCandidates(
                    projection.getName(),
                    projection.getFullName()));
        }
        if (existingNames.isEmpty()) {
            return;
        }
        for (NameCandidate newCandidate : newNames) {
            for (NameCandidate existing : existingNames) {
                int distance = levenshteinDistance(
                        newCandidate.normalized(),
                        existing.normalized());
                if (distance < SIMILAR_NAME_DISTANCE_THRESHOLD) {
                    throw new ValidationException(
                            "Название '" + newCandidate.original()
                                    + "' слишком похоже на уже существующее '"
                                    + existing.original()
                                    + "' (расстояние Левенштейна = " + distance + ")");
                }
            }
        }
    }

    private void validateTypeSpecificConstraints(Organization org) {
        OrganizationType type = org.getType();
        if (type == null) {
            return;
        }
        LongRange employeesRange = EMPLOYEE_CONSTRAINTS.get(type);
        if (employeesRange != null) {
            Long employees = org.getEmployeesCount();
            if (employees == null || !employeesRange.contains(employees)) {
                throw new ValidationException(
                        "Для типа " + type.name()
                                + " допустимое количество сотрудников — от "
                                + employeesRange.min() + " до "
                                + employeesRange.max() + ".");
            }
        }
        FloatRange turnoverRange = TURNOVER_CONSTRAINTS.get(type);
        if (turnoverRange != null) {
            float turnover = org.getAnnualTurnover();
            if (!turnoverRange.contains(turnover)) {
                throw new ValidationException(
                        "Для типа " + type.name()
                                + " годовой оборот должен находиться в диапазоне "
                                + formatFloat(turnoverRange.min()) + " — "
                                + formatFloat(turnoverRange.max()) + ".");
            }
        }
        String prefix = TYPE_NAME_PREFIXES.get(type);
        if (prefix != null && !startsWithIgnoreCase(org.getName(), prefix)) {
            throw new ValidationException(
                    "Название организации для типа "
                            + type.name()
                            + " должно начинаться с \"" + prefix + "\".");
        }
    }

    private List<NameCandidate> collectNameCandidates(String... values) {
        List<NameCandidate> result = new ArrayList<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            result.add(new NameCandidate(
                    trimmed,
                    trimmed.toUpperCase(Locale.ROOT)));
        }
        return result;
    }

    private boolean startsWithIgnoreCase(String value, String prefix) {
        if (value == null || prefix == null) {
            return false;
        }
        String prepared = value.stripLeading();
        return prepared.toUpperCase(Locale.ROOT)
                .startsWith(prefix.toUpperCase(Locale.ROOT));
    }

    private String formatFloat(float value) {
        if (Math.abs(value - Math.round(value)) < 0.0001f) {
            return String.format(Locale.ROOT, "%d", Math.round(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static int levenshteinDistance(String left, String right) {
        int lenLeft = left.length();
        int lenRight = right.length();
        if (lenLeft == 0) {
            return lenRight;
        }
        if (lenRight == 0) {
            return lenLeft;
        }
        int[] previous = new int[lenRight + 1];
        int[] current = new int[lenRight + 1];
        for (int j = 0; j <= lenRight; j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= lenLeft; i++) {
            current[0] = i;
            for (int j = 1; j <= lenRight; j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1)
                        ? 0
                        : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }
        return previous[lenRight];
    }

    private record NameCandidate(String original, String normalized) { }

    private record LongRange(long min, long max) {
        boolean contains(long value) {
            return value >= min && value <= max;
        }
    }

    private record FloatRange(float min, float max) {
        boolean contains(float value) {
            return value >= min && value <= max;
        }
    }

    private void acquireNameLock(String originalName, String normalizedName) {
        if (!databaseLockService.tryAcquire("org_name_" + normalizedName)) {
            throw new ValidationException(NAME_LOCK_BUSY.formatted(originalName));
        }
    }

    private void ensureUniqueName(String name, Integer currentId) {
        if (name == null || name.isBlank()) {
            return;
        }
        String trimmed = name.strip();
        organizationRepository.findByNameIgnoreCase(trimmed)
                .filter(existing -> currentId == null
                        || !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new ValidationException(NAME_ALREADY_EXISTS.formatted(trimmed));
                });
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }

    private void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager
                .isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            r.run();
                        }
                    });
        } else {
            r.run();
        }
    }

    private void scheduleOrphanCleanup() {
        afterCommit(() -> cleanupTransaction.executeWithoutResult(this::cleanupOrphansInternal));
    }

    private void cleanupOrphansInternal(TransactionStatus status) {
        if (!databaseLockService.tryAcquire(ORPHAN_CLEANUP_LOCK)) {
            return;
        }
        if (!executeCleanup(addressRepository::deleteUnassigned, status)) {
            return;
        }
        executeCleanup(coordinatesRepository::deleteUnassigned, status);
    }

    private boolean executeCleanup(Runnable cleanupAction, TransactionStatus status) {
        try {
            cleanupAction.run();
            return true;
        } catch (DataIntegrityViolationException ex) {
            if (status != null) {
                status.setRollbackOnly();
            }
            return false;
        }
    }

    public long countByRatingEquals(double rating) {
        return organizationRepository.countByRating(rating);
    }

    @Transactional(readOnly = true)
    public List<Organization> fullNameStartsWith(String prefix) {
        try (var stream = organizationRepository.streamByFullNameStartingWith(prefix)) {
            return stream
                    .limit(ANALYTICS_STREAM_LIMIT)
                    .toList();
        }
    }

    @Transactional(readOnly = true)
    public List<Organization> fullNameGreaterThan(String value) {
        try (var stream = organizationRepository.streamByFullNameGreaterThan(value)) {
            return stream
                    .limit(ANALYTICS_STREAM_LIMIT)
                    .toList();
        }
    }

    public List<Organization> top5ByTurnover() {
        return organizationRepository.findTop5ByOrderByAnnualTurnoverDesc();
    }

    @Transactional(readOnly = true)
    public double averageEmployeesTop10ByTurnover() {
        try (var top10 = organizationRepository.streamTop10ByOrderByAnnualTurnoverDesc()) {
            return top10
                    .mapToLong(orgValue -> Optional
                            .ofNullable(orgValue.getEmployeesCount())
                            .orElse(0L))
                    .average()
                    .orElse(0d);
        }
    }

    private void applyForm(Organization org, OrganizationForm form) {
        org.setName(form.getName());
        org.setFullName(form.getFullName());
        org.setType(form.getType());
        org.setAnnualTurnover(form.getAnnualTurnover());
        org.setEmployeesCount(form.getEmployeesCount());
        org.setRating(form.getRating());

        // Coordinates
        if (form.getCoordinatesId() != null) {
            Coordinates existing = coordinatesRepository
                    .findByIdForUpdate(form.getCoordinatesId())
                    .orElseThrow(() -> new ValidationException(COORDINATES_NOT_FOUND));
            if (form.getCoordX() != null) {
                existing.setX(form.getCoordX());
            }
            if (form.getCoordY() != null) {
                existing.setY(form.getCoordY());
            }
            org.setCoordinates(existing);
            coordinatesRepository.save(existing);
        } else {
            if (form.getCoordX() == null || form.getCoordY() == null) {
                throw new ValidationException(COORDINATES_REQUIRED);
            }
            Coordinates c = new Coordinates();
            c.setX(form.getCoordX());
            c.setY(form.getCoordY());
            c = coordinatesRepository.save(c);
            org.setCoordinates(c);
        }

        // Official address
        if (form.getOfficialAddressId() != null) {
            Address existing = addressRepository
                    .findByIdForUpdate(form.getOfficialAddressId())
                    .orElseThrow(() -> new ValidationException(OFFICIAL_ADDRESS_NOT_FOUND));
            if (form.getOfficialStreet() != null
                    && !form.getOfficialStreet().isBlank()) {
                existing.setStreet(form.getOfficialStreet());
            }
            if (form.getOfficialZipCode() != null) {
                existing.setZipCode(
                        form.getOfficialZipCode().isBlank()
                                ? null
                                : form.getOfficialZipCode());
            }
            org.setOfficialAddress(existing);
            addressRepository.save(existing);
        } else {
            if (form.getOfficialStreet() == null
                    || form.getOfficialStreet().isBlank()) {
                throw new ValidationException(OFFICIAL_ADDRESS_REQUIRED);
            }
            Address a = new Address();
            a.setStreet(form.getOfficialStreet());
            a.setZipCode(form.getOfficialZipCode());
            a = addressRepository.save(a);
            org.setOfficialAddress(a);
        }

        // Postal address
        if (form.isPostalSameAsOfficial()) {
            org.setPostalAddress(org.getOfficialAddress());
        } else {
            if (form.getPostalAddressId() != null) {
                Address existing = addressRepository
                        .findByIdForUpdate(form.getPostalAddressId())
                        .orElseThrow(() -> new ValidationException(POSTAL_ADDRESS_NOT_FOUND));
                if (form.getPostalStreet() != null
                        && !form.getPostalStreet().isBlank()) {
                    existing.setStreet(form.getPostalStreet());
                }
                if (form.getPostalZipCode() != null) {
                    existing.setZipCode(
                            form.getPostalZipCode().isBlank()
                                    ? null
                                    : form.getPostalZipCode());
                }
                org.setPostalAddress(existing);
                addressRepository.save(existing);
            } else {
                if (form.getPostalStreet() == null
                        || form.getPostalStreet().isBlank()) {
                    throw new ValidationException(POSTAL_ADDRESS_REQUIRED);
                }
                Address a = new Address();
                a.setStreet(form.getPostalStreet());
                a.setZipCode(form.getPostalZipCode());
                a = addressRepository.save(a);
                org.setPostalAddress(a);
            }
        }
    }
}
