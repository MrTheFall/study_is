package com.krusty.crab.service;

import com.krusty.crab.dto.generated.SalesSummary;
import com.krusty.crab.dto.generated.TopMenuItem;
import com.krusty.crab.repository.AnalyticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
            SalesSummary summary = new SalesSummary();
            summary.setFromTs(from != null ? from.atOffset(ZoneOffset.UTC) : null);
            summary.setToTs(to != null ? to.atOffset(ZoneOffset.UTC) : null);
            summary.setOrdersCnt(0);
            summary.setRevenue(BigDecimal.ZERO);
            summary.setAvgTicket(BigDecimal.ZERO);
            return summary;
        }
        
        Object[] row = results.get(0);
        SalesSummary summary = new SalesSummary();
        summary.setFromTs(((LocalDateTime) row[0]).atOffset(ZoneOffset.UTC));
        summary.setToTs(((LocalDateTime) row[1]).atOffset(ZoneOffset.UTC));
        summary.setOrdersCnt(((Number) row[2]).intValue());
        summary.setRevenue((BigDecimal) row[3]);
        summary.setAvgTicket((BigDecimal) row[4]);
        return summary;
    }
    
    public List<TopMenuItem> getTopMenuItems(LocalDateTime from, LocalDateTime to, Integer limit) {
        List<Object[]> results = analyticsRepository.callTopMenuItems(from, to, limit);
        List<TopMenuItem> items = new ArrayList<>();
        
        for (Object[] row : results) {
            TopMenuItem item = new TopMenuItem();
            item.setMenuItemId(((Number) row[0]).intValue());
            item.setName((String) row[1]);
            item.setQuantity(((Number) row[2]).intValue());
            item.setRevenue((BigDecimal) row[3]);
            items.add(item);
        }
        
        return items;
    }
}

