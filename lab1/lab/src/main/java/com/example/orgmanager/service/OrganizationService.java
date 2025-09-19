package com.example.orgmanager.service;

import com.example.orgmanager.model.Address;
import com.example.orgmanager.model.Coordinates;
import com.example.orgmanager.model.Organization;
import com.example.orgmanager.model.OrganizationType;
import com.example.orgmanager.repository.AddressRepository;
import com.example.orgmanager.repository.CoordinatesRepository;
import com.example.orgmanager.repository.OrganizationRepository;
import com.example.orgmanager.web.dto.OrganizationForm;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
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
    private static final int ANALYTICS_STREAM_LIMIT = 200;

    private final OrganizationRepository organizationRepository;
    private final AddressRepository addressRepository;
    private final CoordinatesRepository coordinatesRepository;
    private final OrganizationEventPublisher eventPublisher;
    private final Lock cleanupLock = new ReentrantLock();
    private final TransactionTemplate cleanupTransaction;

    public OrganizationService(OrganizationRepository organizationRepository,
            AddressRepository addressRepository,
            CoordinatesRepository coordinatesRepository,
            OrganizationEventPublisher eventPublisher,
            PlatformTransactionManager transactionManager) {
        this.organizationRepository = organizationRepository;
        this.addressRepository = addressRepository;
        this.coordinatesRepository = coordinatesRepository;
        this.eventPublisher = eventPublisher;
        this.cleanupTransaction = new TransactionTemplate(transactionManager);
        this.cleanupTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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

    @Transactional
    public Organization create(OrganizationForm form) {
        Organization org = new Organization();
        applyForm(org, form);
        Organization saved = organizationRepository.save(org);
        afterCommit(() -> eventPublisher.broadcast("created", saved.getId()));
        return saved;
    }

    @Transactional
    public Organization update(Integer id, OrganizationForm form) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        ORGANIZATION_NOT_FOUND));
        applyForm(org, form);
        Organization saved = organizationRepository.save(org);
        organizationRepository.flush();
        // one-shot orphan cleanup
        scheduleOrphanCleanup();
        afterCommit(() -> eventPublisher.broadcast("updated", saved.getId()));
        return saved;
    }

    @Transactional
    public void delete(Integer id) {
        if (!organizationRepository.existsById(id)) {
            return;
        }
        organizationRepository.deleteById(id);
        organizationRepository.flush();
        scheduleOrphanCleanup();
        afterCommit(() -> eventPublisher.broadcast("deleted", id));
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
        afterCommit(() -> cleanupTransaction.executeWithoutResult(status -> cleanupOrphansInternal()));
    }

    private void cleanupOrphansInternal() {
        cleanupLock.lock();
        try {
            addressRepository.deleteUnassigned();
            coordinatesRepository.deleteUnassigned();
        } finally {
            cleanupLock.unlock();
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
            try {
                Coordinates existing = coordinatesRepository.getReferenceById(
                        form.getCoordinatesId());
                if (form.getCoordX() != null) {
                    existing.setX(form.getCoordX());
                }
                if (form.getCoordY() != null) {
                    existing.setY(form.getCoordY());
                }
                org.setCoordinates(existing);
                coordinatesRepository.save(existing);
            } catch (EntityNotFoundException ex) {
                throw new ValidationException(COORDINATES_NOT_FOUND, ex);
            }
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
            try {
                Address existing = addressRepository.getReferenceById(
                        form.getOfficialAddressId());
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
            } catch (EntityNotFoundException ex) {
                throw new ValidationException(OFFICIAL_ADDRESS_NOT_FOUND, ex);
            }
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
                try {
                    Address existing = addressRepository.getReferenceById(
                            form.getPostalAddressId());
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
                } catch (EntityNotFoundException ex) {
                    throw new ValidationException(POSTAL_ADDRESS_NOT_FOUND, ex);
                }
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
