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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;

@Service
public class OrganizationService {
    private final OrganizationRepository organizationRepository;
    private final AddressRepository addressRepository;
    private final CoordinatesRepository coordinatesRepository;
    private final OrganizationEventPublisher eventPublisher;

    public OrganizationService(OrganizationRepository organizationRepository,
                               AddressRepository addressRepository,
                               CoordinatesRepository coordinatesRepository,
                               OrganizationEventPublisher eventPublisher) {
        this.organizationRepository = organizationRepository;
        this.addressRepository = addressRepository;
        this.coordinatesRepository = coordinatesRepository;
        this.eventPublisher = eventPublisher;
    }

    public Page<Organization> list(@Nullable String filterField,
                                   @Nullable String filterValue,
                                   Pageable pageable) {
        if (filterField == null || filterField.isBlank() || filterValue == null) {
            return organizationRepository.findAll(pageable);
        }
        return switch (filterField) {
            case "name" -> organizationRepository.findByName(filterValue, pageable);
            case "fullName" -> organizationRepository.findByFullName(filterValue, pageable);
            case "officialStreet" -> organizationRepository.findByOfficialAddress_Street(filterValue, pageable);
            case "postalStreet" -> organizationRepository.findByPostalAddress_Street(filterValue, pageable);
            case "type" -> {
                OrganizationType type = null;
                try { type = OrganizationType.valueOf(filterValue); } catch (Exception ignored) {}
                yield (type != null) ? organizationRepository.findByType(type, pageable) : Page.empty(pageable);
            }
            default -> organizationRepository.findAll(pageable);
        };
    }

    public Optional<Organization> findById(Integer id) {
        return organizationRepository.findById(id);
    }

    public List<Address> allAddresses() { return addressRepository.findAll(); }
    public List<Coordinates> allCoordinates() { return coordinatesRepository.findAll(); }

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
        Organization org = organizationRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Organization not found"));
        applyForm(org, form);
        Organization saved = organizationRepository.save(org);
        organizationRepository.flush();
        // one-shot orphan cleanup
        addressRepository.deleteUnassigned();
        coordinatesRepository.deleteUnassigned();
        afterCommit(() -> eventPublisher.broadcast("updated", saved.getId()));
        return saved;
    }

    @Transactional
    public void delete(Integer id) {
        if (!organizationRepository.existsById(id)) return;
        organizationRepository.deleteById(id);
        organizationRepository.flush();
        addressRepository.deleteUnassigned();
        coordinatesRepository.deleteUnassigned();
        afterCommit(() -> eventPublisher.broadcast("deleted", id));
    }

    private void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { r.run(); }
            });
        } else {
            r.run();
        }
    }


    public long countByRatingEquals(double rating) {
        return organizationRepository.countByRating(rating);
    }

    public List<Organization> fullNameStartsWith(String prefix) {
        return organizationRepository.findByFullNameStartingWith(prefix);
    }

    public List<Organization> fullNameGreaterThan(String value) {
        return organizationRepository.findByFullNameGreaterThan(value);
    }

    public List<Organization> top5ByTurnover() {
        return organizationRepository.findTop5ByOrderByAnnualTurnoverDesc();
    }

    public double averageEmployeesTop10ByTurnover() {
        var top10 = organizationRepository.findTop10ByOrderByAnnualTurnoverDesc();
        if (top10.isEmpty()) return 0d;
        return top10.stream().mapToLong(o -> Optional.ofNullable(o.getEmployeesCount()).orElse(0L)).average().orElse(0d);
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
            Coordinates existing = coordinatesRepository.findById(form.getCoordinatesId())
                    .orElseThrow(() -> new ValidationException("Выбранные координаты не найдены"));
            if (form.getCoordX() != null) existing.setX(form.getCoordX());
            if (form.getCoordY() != null) existing.setY(form.getCoordY());
            org.setCoordinates(existing);
            coordinatesRepository.save(existing);
        } else {
            if (form.getCoordX() == null || form.getCoordY() == null)
                throw new ValidationException("Требуется указать координаты X и Y, если не выбран существующий вариант");
            Coordinates c = new Coordinates();
            c.setX(form.getCoordX());
            c.setY(form.getCoordY());
            c = coordinatesRepository.save(c);
            org.setCoordinates(c);
        }

        // Official address
        if (form.getOfficialAddressId() != null) {
            Address existing = addressRepository.findById(form.getOfficialAddressId())
                    .orElseThrow(() -> new ValidationException("Выбранный официальный адрес не найден"));
            if (form.getOfficialStreet() != null && !form.getOfficialStreet().isBlank()) {
                existing.setStreet(form.getOfficialStreet());
            }
            if (form.getOfficialZipCode() != null) {
                existing.setZipCode(form.getOfficialZipCode().isBlank() ? null : form.getOfficialZipCode());
            }
            org.setOfficialAddress(existing);
            addressRepository.save(existing);
        } else {
            if (form.getOfficialStreet() == null || form.getOfficialStreet().isBlank())
                throw new ValidationException("Требуется указать улицу официального адреса, если не выбран существующий");
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
                Address existing = addressRepository.findById(form.getPostalAddressId())
                        .orElseThrow(() -> new ValidationException("Выбранный почтовый адрес не найден"));
                if (form.getPostalStreet() != null && !form.getPostalStreet().isBlank()) {
                    existing.setStreet(form.getPostalStreet());
                }
                if (form.getPostalZipCode() != null) {
                    existing.setZipCode(form.getPostalZipCode().isBlank() ? null : form.getPostalZipCode());
                }
                org.setPostalAddress(existing);
                addressRepository.save(existing);
            } else {
                if (form.getPostalStreet() == null || form.getPostalStreet().isBlank())
                    throw new ValidationException("Требуется указать улицу почтового адреса, если не выбран существующий");
                Address a = new Address();
                a.setStreet(form.getPostalStreet());
                a.setZipCode(form.getPostalZipCode());
                a = addressRepository.save(a);
                org.setPostalAddress(a);
            }
        }
    }
}
