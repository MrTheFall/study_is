package com.krusty.crab.service;

import com.krusty.crab.entity.Employee;
import com.krusty.crab.entity.SalaryPayment;
import com.krusty.crab.exception.EntityNotFoundException;
import com.krusty.crab.exception.ValidationException;
import com.krusty.crab.repository.EmployeeRepository;
import com.krusty.crab.repository.SalaryPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SalaryPaymentService {

    private final SalaryPaymentRepository salaryPaymentRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional
    public SalaryPayment create(Integer employeeId, BigDecimal amount, String note, OffsetDateTime paidAt) {
        if (employeeId == null) {
            throw new ValidationException("employeeId is required");
        }
        if (amount == null) {
            throw new ValidationException("amount is required");
        }
        if (amount.signum() < 0) {
            throw new ValidationException("amount must be non-negative");
        }

        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("Employee", employeeId));

        LocalDateTime paidAtLocal = paidAt != null ? paidAt.toLocalDateTime() : LocalDateTime.now(ZoneOffset.UTC);

        SalaryPayment payment = SalaryPayment.builder()
            .employee(employee)
            .amount(amount)
            .note(note)
            .paidAt(paidAtLocal)
            .build();

        return salaryPaymentRepository.save(payment);
    }

    public List<SalaryPayment> list(Integer employeeId, OffsetDateTime from, OffsetDateTime to, Integer limit, Integer offset) {
        int resolvedLimit = limit != null ? limit : 50;
        int resolvedOffset = offset != null ? offset : 0;
        if (resolvedLimit < 1) resolvedLimit = 1;
        if (resolvedLimit > 500) resolvedLimit = 500;
        if (resolvedOffset < 0) resolvedOffset = 0;

        LocalDateTime fromLocal = from != null ? from.toLocalDateTime() : null;
        LocalDateTime toLocal = to != null ? to.toLocalDateTime() : null;

        return salaryPaymentRepository.findRecent(employeeId, fromLocal, toLocal, resolvedLimit, resolvedOffset);
    }
}

