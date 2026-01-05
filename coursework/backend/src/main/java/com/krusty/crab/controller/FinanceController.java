package com.krusty.crab.controller;

import com.krusty.crab.api.FinanceApi;
import com.krusty.crab.dto.generated.SalaryPayment;
import com.krusty.crab.dto.generated.SalaryPaymentCreateRequest;
import com.krusty.crab.mapper.SalaryPaymentMapper;
import com.krusty.crab.service.EmployeeActionLogService;
import com.krusty.crab.service.SalaryPaymentService;
import com.krusty.crab.util.AuditActions;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('Manager')")
public class FinanceController implements FinanceApi {

    private final SalaryPaymentService salaryPaymentService;
    private final SalaryPaymentMapper salaryPaymentMapper;
    private final EmployeeActionLogService actionLogService;

    @Override
    public ResponseEntity<List<SalaryPayment>> getSalaryPayments(
            Integer employeeId, OffsetDateTime from, OffsetDateTime to, Integer limit, Integer offset) {
        log.info(
                "Getting salary payments, employeeId: {}, from: {}, to: {}, limit: {}, offset: {}",
                employeeId,
                from,
                to,
                limit,
                offset);
        var payments = salaryPaymentService.list(employeeId, from, to, limit, offset);
        return ResponseEntity.ok(salaryPaymentMapper.toDtoList(payments));
    }

    @Override
    public ResponseEntity<SalaryPayment> createSalaryPayment(SalaryPaymentCreateRequest salaryPaymentCreateRequest) {
        log.info("Creating salary payment for employee {}", salaryPaymentCreateRequest.getEmployeeId());
        var payment = salaryPaymentService.create(
                salaryPaymentCreateRequest.getEmployeeId(),
                salaryPaymentCreateRequest.getAmount(),
                salaryPaymentCreateRequest.getNote(),
                salaryPaymentCreateRequest.getPaidAt());
        String details = String.format(
                "employeeId=%s, amount=%s",
                salaryPaymentCreateRequest.getEmployeeId(), salaryPaymentCreateRequest.getAmount());
        actionLogService.logCurrentEmployeeAction(
                AuditActions.SALARY_PAYMENT_CREATE, "salary_payment", payment.getId(), details);
        return ResponseEntity.status(HttpStatus.CREATED).body(salaryPaymentMapper.toDto(payment));
    }
}
