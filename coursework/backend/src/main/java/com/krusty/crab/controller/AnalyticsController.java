package com.krusty.crab.controller;

import com.krusty.crab.api.AnalyticsApi;
import com.krusty.crab.dto.generated.SalesSummary;
import com.krusty.crab.dto.generated.TopMenuItem;
import com.krusty.crab.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class AnalyticsController implements AnalyticsApi {
    
    private final AnalyticsService analyticsService;
    
    @Override
    public ResponseEntity<SalesSummary> getSalesSummary(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        log.info("Getting sales summary from {} to {}", from, to);
        LocalDateTime fromLocal = from != null ? from.toLocalDateTime() : null;
        LocalDateTime toLocal = to != null ? to.toLocalDateTime() : null;
        SalesSummary summary = analyticsService.getSalesSummary(fromLocal, toLocal);
        return ResponseEntity.ok(summary);
    }
    
    @Override
    public ResponseEntity<List<TopMenuItem>> getTopMenuItems(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            Integer limit) {
        log.info("Getting top menu items from {} to {} with limit {}", from, to, limit);
        LocalDateTime fromLocal = from != null ? from.toLocalDateTime() : null;
        LocalDateTime toLocal = to != null ? to.toLocalDateTime() : null;
        List<TopMenuItem> items = analyticsService.getTopMenuItems(fromLocal, toLocal, limit);
        return ResponseEntity.ok(items);
    }
}



