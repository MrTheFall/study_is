package com.krusty.crab.controller;

import com.krusty.crab.api.AuditApi;
import com.krusty.crab.mapper.EmployeeActionLogMapper;
import com.krusty.crab.service.EmployeeActionLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('Manager')")
public class AuditController implements AuditApi {

    private final EmployeeActionLogService actionLogService;
    private final EmployeeActionLogMapper actionLogMapper;

    @Override
    public ResponseEntity<List<com.krusty.crab.dto.generated.AuditLogEntry>> getAuditLogs(
        Integer employeeId,
        Integer orderId,
        String action,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
        Integer limit,
        Integer offset
    ) {
        log.info("Getting audit logs, employeeId: {}, orderId: {}, action: {}, limit: {}, offset: {}", employeeId, orderId, action, limit, offset);
        List<com.krusty.crab.entity.EmployeeActionLog> logs = actionLogService.getLogs(
            employeeId, orderId, action, from, to, limit, offset
        );
        return ResponseEntity.ok(actionLogMapper.toDtoList(logs));
    }
}
