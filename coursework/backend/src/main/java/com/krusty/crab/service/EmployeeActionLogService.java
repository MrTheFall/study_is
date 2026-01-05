package com.krusty.crab.service;

import com.krusty.crab.entity.Employee;
import com.krusty.crab.entity.EmployeeActionLog;
import com.krusty.crab.repository.EmployeeActionLogRepository;
import com.krusty.crab.repository.EmployeeRepository;
import com.krusty.crab.security.SecurityContext;
import com.krusty.crab.security.UserPrincipal;
import com.krusty.crab.security.UserType;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmployeeActionLogService {

    private final EmployeeActionLogRepository actionLogRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional
    public void logAction(Integer employeeId, String action, String entityType, Integer entityId, String details) {
        logAction(employeeId, action, entityType, entityId, null, null, null, details);
    }

    @Transactional
    public void logAction(
            Integer employeeId, String action, String entityType, Integer entityId, Integer orderId, String details) {
        logAction(employeeId, action, entityType, entityId, orderId, null, null, details);
    }

    @Transactional
    public void logAction(
            Integer employeeId,
            String action,
            String entityType,
            Integer entityId,
            Integer orderId,
            String fromValue,
            String toValue) {
        logAction(employeeId, action, entityType, entityId, orderId, fromValue, toValue, null);
    }

    @Transactional
    public void logOrderAction(Integer employeeId, String action, Integer orderId, String details) {
        logAction(employeeId, action, "order", orderId, orderId, null, null, details);
    }

    @Transactional
    public void logOrderAction(Integer employeeId, String action, Integer orderId, String fromValue, String toValue) {
        logAction(employeeId, action, "order", orderId, orderId, fromValue, toValue, null);
    }

    @Transactional
    public void logAction(
            Integer employeeId,
            String action,
            String entityType,
            Integer entityId,
            Integer orderId,
            String fromValue,
            String toValue,
            String details) {
        if (employeeId == null || action == null || action.isBlank()) {
            return;
        }

        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        if (employee == null) {
            return;
        }

        EmployeeActionLog entry = EmployeeActionLog.builder()
                .employee(employee)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .orderId(orderId)
                .fromValue(fromValue)
                .toValue(toValue)
                .details(details)
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();

        actionLogRepository.save(entry);
    }

    @Transactional
    public void logCurrentEmployeeAction(String action, String entityType, Integer entityId) {
        logCurrentEmployeeAction(action, entityType, entityId, null, null, null, null);
    }

    @Transactional
    public void logCurrentEmployeeAction(String action, String entityType, Integer entityId, String details) {
        logCurrentEmployeeAction(action, entityType, entityId, null, null, null, details);
    }

    @Transactional
    public void logCurrentEmployeeOrderAction(String action, Integer orderId, String details) {
        logCurrentEmployeeAction(action, "order", orderId, orderId, null, null, details);
    }

    @Transactional
    public void logCurrentEmployeeOrderAction(String action, Integer orderId, String fromValue, String toValue) {
        logCurrentEmployeeAction(action, "order", orderId, orderId, fromValue, toValue, null);
    }

    @Transactional
    public void logCurrentEmployeeAction(
            String action,
            String entityType,
            Integer entityId,
            Integer orderId,
            String fromValue,
            String toValue,
            String details) {
        UserPrincipal user;
        try {
            user = SecurityContext.getCurrentUser();
        } catch (Exception e) {
            return;
        }

        if (user == null || user.getUserType() != UserType.EMPLOYEE) {
            return;
        }

        logAction(user.getUserId(), action, entityType, entityId, orderId, fromValue, toValue, details);
    }

    public List<EmployeeActionLog> getLogs(
            Integer employeeId,
            Integer orderId,
            String action,
            OffsetDateTime from,
            OffsetDateTime to,
            Integer limit,
            Integer offset) {
        int resolvedLimit = limit != null ? limit : 50;
        int resolvedOffset = offset != null ? offset : 0;
        if (resolvedLimit < 1) resolvedLimit = 1;
        if (resolvedLimit > 500) resolvedLimit = 500;
        if (resolvedOffset < 0) resolvedOffset = 0;

        LocalDateTime fromLocal = from != null ? from.toLocalDateTime() : null;
        LocalDateTime toLocal = to != null ? to.toLocalDateTime() : null;

        return actionLogRepository.findRecent(
                employeeId, orderId, action, fromLocal, toLocal, resolvedLimit, resolvedOffset);
    }
}
