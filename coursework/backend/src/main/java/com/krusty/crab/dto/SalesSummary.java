package com.krusty.crab.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesSummary {
    private LocalDateTime fromTs;
    private LocalDateTime toTs;
    private Integer ordersCnt;
    private BigDecimal revenue;
    private BigDecimal avgTicket;
}

