package com.krusty.crab.controller;

import com.krusty.crab.api.FinanceApi;
import com.krusty.crab.dto.generated.SalaryPaymentCreateRequest;
import com.krusty.crab.mapper.SalaryPaymentMapper;
import com.krusty.crab.service.SalaryPaymentService;
import com.krusty.crab.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class FinanceController implements FinanceApi {

    private final SalaryPaymentService salaryPaymentService;
    private final SalaryPaymentMapper salaryPaymentMapper;

    @Override
    public ResponseEntity<List<com.krusty.crab.dto.generated.SalaryPayment>> getSalaryPayments(
        Integer employeeId,
        java.time.OffsetDateTime from,
        java.time.OffsetDateTime to,
        Integer limit,
        Integer offset
    ) {
        SecurityUtil.requireRole("Manager");
        log.info("Getting salary payments, employeeId: {}, from: {}, to: {}, limit: {}, offset: {}", employeeId, from, to, limit, offset);
        List<com.krusty.crab.entity.SalaryPayment> payments = salaryPaymentService.list(employeeId, from, to, limit, offset);
        return ResponseEntity.ok(salaryPaymentMapper.toDtoList(payments));
    }

    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.SalaryPayment> createSalaryPayment(SalaryPaymentCreateRequest salaryPaymentCreateRequest) {
        SecurityUtil.requireRole("Manager");
        log.info("Creating salary payment for employee {}", salaryPaymentCreateRequest.getEmployeeId());
        com.krusty.crab.entity.SalaryPayment payment = salaryPaymentService.create(
            salaryPaymentCreateRequest.getEmployeeId(),
            salaryPaymentCreateRequest.getAmount(),
            salaryPaymentCreateRequest.getNote(),
            salaryPaymentCreateRequest.getPaidAt()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(salaryPaymentMapper.toDto(payment));
    }
}

