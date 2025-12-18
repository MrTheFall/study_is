package com.krusty.crab.controller;

import com.krusty.crab.api.CouriersApi;
import com.krusty.crab.dto.generated.CourierCreateRequest;
import com.krusty.crab.entity.Courier;
import com.krusty.crab.mapper.CourierMapper;
import com.krusty.crab.service.CourierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class CouriersController implements CouriersApi {

    private final CourierService courierService;
    private final CourierMapper courierMapper;

    @Override
    @PreAuthorize("hasRole('Manager') or hasRole('Cashier')")
    public ResponseEntity<List<com.krusty.crab.dto.generated.Courier>> getAllCouriers() {
        log.info("Getting all couriers");
        List<Courier> couriers = courierService.getAllCouriers();
        return ResponseEntity.ok(courierMapper.toDtoList(couriers));
    }

    @Override
    @PreAuthorize("hasRole('Manager')")
    public ResponseEntity<com.krusty.crab.dto.generated.Courier> createCourier(CourierCreateRequest courierCreateRequest) {
        log.info("Creating courier with phone: {}", courierCreateRequest.getPhone());
        Courier courier = courierMapper.toEntity(courierCreateRequest);
        Courier saved = courierService.createCourier(courier);
        return ResponseEntity.status(HttpStatus.CREATED).body(courierMapper.toDto(saved));
    }

    @Override
    @PreAuthorize("hasRole('Manager') or hasRole('Cashier')")
    public ResponseEntity<com.krusty.crab.dto.generated.Courier> getCourierById(Integer courierId) {
        log.info("Getting courier by id: {}", courierId);
        Courier courier = courierService.getCourierById(courierId);
        return ResponseEntity.ok(courierMapper.toDto(courier));
    }

    @Override
    @PreAuthorize("hasRole('Manager')")
    public ResponseEntity<com.krusty.crab.dto.generated.Courier> updateCourier(Integer courierId, CourierCreateRequest courierCreateRequest) {
        log.info("Updating courier {}, phone: {}", courierId, courierCreateRequest.getPhone());
        Courier courierData = courierMapper.toEntity(courierCreateRequest);
        Courier updated = courierService.updateCourier(courierId, courierData);
        return ResponseEntity.ok(courierMapper.toDto(updated));
    }

    @Override
    @PreAuthorize("hasRole('Manager')")
    public ResponseEntity<Void> deleteCourier(Integer courierId) {
        log.info("Deleting courier {}", courierId);
        courierService.deleteCourier(courierId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
