package com.krusty.crab.service;

import com.krusty.crab.entity.Courier;
import com.krusty.crab.entity.enums.OrderStatus;
import com.krusty.crab.exception.DuplicateEntityException;
import com.krusty.crab.exception.EntityNotFoundException;
import com.krusty.crab.exception.ValidationException;
import com.krusty.crab.repository.CourierRepository;
import com.krusty.crab.repository.OrderRepository;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourierService {

    private final CourierRepository courierRepository;
    private final OrderRepository orderRepository;

    private static final List<OrderStatus> BUSY_STATUSES = Arrays.asList(
            OrderStatus.PENDING,
            OrderStatus.CONFIRMED,
            OrderStatus.PREPARING,
            OrderStatus.READY,
            OrderStatus.DELIVERING);

    public List<Courier> getAllCouriers() {
        List<Courier> couriers = courierRepository.findAll();
        for (Courier courier : couriers) {
            courier.setBusy(isCourierBusy(courier.getId()));
        }
        return couriers;
    }

    public Courier getCourierById(Integer courierId) {
        Courier courier = courierRepository
                .findById(courierId)
                .orElseThrow(() -> new EntityNotFoundException("Courier", courierId));
        courier.setBusy(isCourierBusy(courier.getId()));
        return courier;
    }

    @Transactional
    public Courier createCourier(Courier courier) {
        if (courier.getName() == null || courier.getName().trim().isEmpty()) {
            throw new ValidationException("name", "must not be blank");
        }
        if (courier.getPhone() == null || courier.getPhone().trim().isEmpty()) {
            throw new ValidationException("phone", "must not be blank");
        }

        if (courierRepository.findByPhone(courier.getPhone()).isPresent()) {
            throw new DuplicateEntityException("Courier", "phone", courier.getPhone());
        }

        if (courier.getAvailable() == null) {
            courier.setAvailable(true);
        }

        Courier saved = courierRepository.save(courier);
        saved.setBusy(false);
        log.info("Courier created with ID: {}", saved.getId());
        return saved;
    }

    @Transactional
    public Courier updateCourier(Integer courierId, Courier courierData) {
        Courier courier = getCourierById(courierId);

        if (courierData.getName() != null) {
            courier.setName(courierData.getName());
        }

        if (courierData.getPhone() != null && !courierData.getPhone().equals(courier.getPhone())) {
            if (courierRepository.findByPhone(courierData.getPhone()).isPresent()) {
                throw new DuplicateEntityException("Courier", "phone", courierData.getPhone());
            }
            courier.setPhone(courierData.getPhone());
        }

        if (courierData.getVehicleInfo() != null) {
            courier.setVehicleInfo(courierData.getVehicleInfo());
        }

        if (courierData.getAvailable() != null) {
            courier.setAvailable(courierData.getAvailable());
        }

        Courier updated = courierRepository.save(courier);
        updated.setBusy(isCourierBusy(updated.getId()));
        log.info("Courier {} updated", courierId);
        return updated;
    }

    @Transactional
    public void deleteCourier(Integer courierId) {
        Courier courier = getCourierById(courierId);
        courierRepository.delete(courier);
        log.info("Courier {} deleted", courierId);
    }

    private boolean isCourierBusy(Integer courierId) {
        if (courierId == null) {
            return false;
        }
        return orderRepository.existsByCourier_IdAndStatusIn(courierId, BUSY_STATUSES);
    }
}
