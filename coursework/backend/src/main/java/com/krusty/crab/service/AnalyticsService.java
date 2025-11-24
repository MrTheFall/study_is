package com.krusty.crab.service;

import com.krusty.crab.dto.SalesSummary;
import com.krusty.crab.dto.TopMenuItem;
import com.krusty.crab.repository.AnalyticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {
    
    private final AnalyticsRepository analyticsRepository;
    
    public SalesSummary getSalesSummary(LocalDateTime from, LocalDateTime to) {
        List<Object[]> results = analyticsRepository.callSalesSummary(from, to);
        
        if (results.isEmpty()) {
            return SalesSummary.builder()
                .fromTs(from)
                .toTs(to)
                .ordersCnt(0)
                .revenue(BigDecimal.ZERO)
                .avgTicket(BigDecimal.ZERO)
                .build();
        }
        
        Object[] row = results.get(0);
        return SalesSummary.builder()
            .fromTs((LocalDateTime) row[0])
            .toTs((LocalDateTime) row[1])
            .ordersCnt(((Number) row[2]).intValue())
            .revenue((BigDecimal) row[3])
            .avgTicket((BigDecimal) row[4])
            .build();
    }
    
    public List<TopMenuItem> getTopMenuItems(LocalDateTime from, LocalDateTime to, Integer limit) {
        List<Object[]> results = analyticsRepository.callTopMenuItems(from, to, limit);
        List<TopMenuItem> items = new ArrayList<>();
        
        for (Object[] row : results) {
            TopMenuItem item = TopMenuItem.builder()
                .menuItemId(((Number) row[0]).intValue())
                .name((String) row[1])
                .quantity(((Number) row[2]).longValue())
                .revenue((BigDecimal) row[3])
                .build();
            items.add(item);
        }
        
        return items;
    }
}

