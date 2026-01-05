package com.krusty.crab.controller;

import com.krusty.crab.api.AnalyticsApi;
import com.krusty.crab.dto.generated.FinancialSummary;
import com.krusty.crab.dto.generated.SalesByEmployeeItem;
import com.krusty.crab.dto.generated.SalesByTimeOfDayItem;
import com.krusty.crab.dto.generated.SalesSummary;
import com.krusty.crab.dto.generated.TopMenuItem;
import com.krusty.crab.mapper.ReportViewMapper;
import com.krusty.crab.security.SecurityContext;
import com.krusty.crab.security.UserPrincipal;
import com.krusty.crab.service.AnalyticsService;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('Manager')")
public class AnalyticsController implements AnalyticsApi {

    private final AnalyticsService analyticsService;
    private final ReportViewMapper reportViewMapper;

    @Override
    public ResponseEntity<SalesSummary> getSalesSummary(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        UserPrincipal user = SecurityContext.getCurrentUser();
        log.info("Getting sales summary from {} to {}", from, to);
        LocalDateTime fromLocal = toLocalDateTimeOrNull(from);
        LocalDateTime toLocal = toLocalDateTimeOrNull(to);
        analyticsService.logReportView(user.getUserId(), "sales_summary", fromLocal, toLocal);
        SalesSummary summary = analyticsService.getSalesSummary(fromLocal, toLocal);
        return ResponseEntity.ok(summary);
    }

    @Override
    public ResponseEntity<List<TopMenuItem>> getTopMenuItems(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            Integer limit) {
        UserPrincipal user = SecurityContext.getCurrentUser();
        log.info("Getting top menu items from {} to {} with limit {}", from, to, limit);
        LocalDateTime fromLocal = toLocalDateTimeOrNull(from);
        LocalDateTime toLocal = toLocalDateTimeOrNull(to);
        analyticsService.logReportView(user.getUserId(), "top_menu_items", fromLocal, toLocal);
        List<TopMenuItem> items = analyticsService.getTopMenuItems(fromLocal, toLocal, limit);
        return ResponseEntity.ok(items);
    }

    @Override
    public ResponseEntity<List<SalesByEmployeeItem>> getSalesByEmployee(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        UserPrincipal user = SecurityContext.getCurrentUser();
        log.info("Getting sales by employee from {} to {}", from, to);
        LocalDateTime fromLocal = toLocalDateTimeOrNull(from);
        LocalDateTime toLocal = toLocalDateTimeOrNull(to);
        analyticsService.logReportView(user.getUserId(), "sales_by_employee", fromLocal, toLocal);
        List<SalesByEmployeeItem> items = analyticsService.getSalesByEmployee(fromLocal, toLocal);
        return ResponseEntity.ok(items);
    }

    @Override
    public ResponseEntity<List<SalesByTimeOfDayItem>> getSalesByTimeOfDay(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        UserPrincipal user = SecurityContext.getCurrentUser();
        log.info("Getting sales by time of day from {} to {}", from, to);
        LocalDateTime fromLocal = toLocalDateTimeOrNull(from);
        LocalDateTime toLocal = toLocalDateTimeOrNull(to);
        analyticsService.logReportView(user.getUserId(), "sales_by_time_of_day", fromLocal, toLocal);
        List<SalesByTimeOfDayItem> items = analyticsService.getSalesByTimeOfDay(fromLocal, toLocal);
        return ResponseEntity.ok(items);
    }

    @Override
    public ResponseEntity<List<com.krusty.crab.dto.generated.ReportView>> getReportViews(
            Integer limit, Integer offset) {
        log.info("Getting report views history, limit: {}, offset: {}", limit, offset);
        List<com.krusty.crab.entity.ReportView> views = analyticsService.getReportViews(limit, offset);
        return ResponseEntity.ok(reportViewMapper.toDtoList(views));
    }

    @Override
    public ResponseEntity<FinancialSummary> getFinancialSummary(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        UserPrincipal user = SecurityContext.getCurrentUser();
        log.info("Getting financial summary from {} to {}", from, to);
        LocalDateTime fromLocal = toLocalDateTimeOrNull(from);
        LocalDateTime toLocal = toLocalDateTimeOrNull(to);
        analyticsService.logReportView(user.getUserId(), "financial_summary", fromLocal, toLocal);
        FinancialSummary summary = analyticsService.getFinancialSummary(fromLocal, toLocal);
        return ResponseEntity.ok(summary);
    }

    private static LocalDateTime toLocalDateTimeOrNull(OffsetDateTime value) {
        return Optional.ofNullable(value).map(OffsetDateTime::toLocalDateTime).orElse(null);
    }
}
