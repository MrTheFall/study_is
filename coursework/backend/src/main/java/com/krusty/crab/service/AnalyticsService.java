package com.krusty.crab.service;

import com.krusty.crab.dto.generated.FinancialSummary;
import com.krusty.crab.dto.generated.SalesSummary;
import com.krusty.crab.dto.generated.SalesByEmployeeItem;
import com.krusty.crab.dto.generated.SalesByTimeOfDayItem;
import com.krusty.crab.dto.generated.TopMenuItem;
import com.krusty.crab.entity.Employee;
import com.krusty.crab.entity.ReportView;
import com.krusty.crab.repository.AnalyticsRepository;
import com.krusty.crab.repository.EmployeeRepository;
import com.krusty.crab.repository.ReportViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ReportViewRepository reportViewRepository;
    private final EmployeeRepository employeeRepository;

    public SalesSummary getSalesSummary(LocalDateTime from, LocalDateTime to) {
        List<Object[]> results = analyticsRepository.callSalesSummary(from, to);

        if (results.isEmpty()) {
            SalesSummary summary = new SalesSummary();
            summary.setFromTs(from != null ? from.atOffset(ZoneOffset.UTC) : null);
            summary.setToTs(to != null ? to.atOffset(ZoneOffset.UTC) : null);
            summary.setOrdersCnt(0);
            summary.setRevenue(BigDecimal.ZERO);
            summary.setAvgTicket(BigDecimal.ZERO);
            summary.setHasData(false);
            return summary;
        }

        Object[] row = results.get(0);
        SalesSummary summary = new SalesSummary();
        summary.setFromTs(row[0] != null ? ((LocalDateTime) row[0]).atOffset(ZoneOffset.UTC) : null);
        summary.setToTs(row[1] != null ? ((LocalDateTime) row[1]).atOffset(ZoneOffset.UTC) : null);
        summary.setOrdersCnt(((Number) row[2]).intValue());
        summary.setRevenue(row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO);
        summary.setAvgTicket(row[4] != null ? (BigDecimal) row[4] : BigDecimal.ZERO);
        summary.setHasData(summary.getOrdersCnt() != null && summary.getOrdersCnt() > 0);
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

    public List<SalesByEmployeeItem> getSalesByEmployee(LocalDateTime from, LocalDateTime to) {
        List<Object[]> results = analyticsRepository.callSalesByEmployee(from, to);
        List<SalesByEmployeeItem> items = new ArrayList<>();

        for (Object[] row : results) {
            SalesByEmployeeItem item = new SalesByEmployeeItem();
            item.setEmployeeId(row[0] != null ? ((Number) row[0]).intValue() : null);
            item.setEmployeeName(row[1] != null ? row[1].toString() : null);
            item.setOrdersCnt(row[2] != null ? ((Number) row[2]).intValue() : 0);
            item.setRevenue(row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO);
            item.setAvgTicket(row[4] != null ? (BigDecimal) row[4] : BigDecimal.ZERO);
            items.add(item);
        }

        return items;
    }

    public List<SalesByTimeOfDayItem> getSalesByTimeOfDay(LocalDateTime from, LocalDateTime to) {
        List<Object[]> results = analyticsRepository.callSalesByTimeOfDay(from, to);
        List<SalesByTimeOfDayItem> items = new ArrayList<>();

        for (Object[] row : results) {
            SalesByTimeOfDayItem item = new SalesByTimeOfDayItem();
            item.setBucket(row[0] != null ? row[0].toString() : null);
            item.setOrdersCnt(row[1] != null ? ((Number) row[1]).intValue() : 0);
            item.setRevenue(row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO);
            item.setAvgTicket(row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO);
            items.add(item);
        }

        return items;
    }

    public FinancialSummary getFinancialSummary(LocalDateTime from, LocalDateTime to) {
        List<Object[]> results = analyticsRepository.callFinancialSummary(from, to);

        if (results.isEmpty()) {
            FinancialSummary summary = new FinancialSummary();
            summary.setFrom(from != null ? from.atOffset(ZoneOffset.UTC) : null);
            summary.setTo(to != null ? to.atOffset(ZoneOffset.UTC) : null);
            summary.setRevenue(BigDecimal.ZERO);
            summary.setIngredientExpenses(BigDecimal.ZERO);
            summary.setSalaryExpenses(BigDecimal.ZERO);
            summary.setProfit(BigDecimal.ZERO);
            summary.setHasData(false);
            return summary;
        }

        Object[] row = results.get(0);
        BigDecimal revenue = row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO;
        BigDecimal ingredientExpenses = row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO;
        BigDecimal salaryExpenses = row[4] != null ? (BigDecimal) row[4] : BigDecimal.ZERO;
        boolean hasData = revenue.compareTo(BigDecimal.ZERO) != 0
            || ingredientExpenses.compareTo(BigDecimal.ZERO) != 0
            || salaryExpenses.compareTo(BigDecimal.ZERO) != 0;

        FinancialSummary summary = new FinancialSummary();
        summary.setFrom(row[0] != null ? ((LocalDateTime) row[0]).atOffset(ZoneOffset.UTC) : null);
        summary.setTo(row[1] != null ? ((LocalDateTime) row[1]).atOffset(ZoneOffset.UTC) : null);
        summary.setRevenue(revenue);
        summary.setIngredientExpenses(ingredientExpenses);
        summary.setSalaryExpenses(salaryExpenses);
        summary.setProfit(row[5] != null ? (BigDecimal) row[5] : BigDecimal.ZERO);
        summary.setHasData(hasData);
        return summary;
    }

    @Transactional
    public void logReportView(Integer employeeId, String report, LocalDateTime from, LocalDateTime to) {
        Employee employee = null;
        if (employeeId != null) {
            employee = employeeRepository.findById(employeeId).orElse(null);
        }

        ReportView view = ReportView.builder()
            .employee(employee)
            .report(report)
            .fromTs(from)
            .toTs(to)
            .viewedAt(LocalDateTime.now(ZoneOffset.UTC))
            .build();
        reportViewRepository.save(view);
    }

    public List<ReportView> getReportViews(Integer limit, Integer offset) {
        int resolvedLimit = limit != null ? limit : 50;
        int resolvedOffset = offset != null ? offset : 0;

        if (resolvedLimit < 1) {
            resolvedLimit = 1;
        }
        if (resolvedLimit > 500) {
            resolvedLimit = 500;
        }
        if (resolvedOffset < 0) {
            resolvedOffset = 0;
        }

        return reportViewRepository.findRecent(resolvedLimit, resolvedOffset);
    }
}
