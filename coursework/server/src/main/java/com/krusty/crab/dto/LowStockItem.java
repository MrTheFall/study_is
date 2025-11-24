package com.krusty.crab.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LowStockItem {
    private Integer ingredientId;
    private String name;
    private BigDecimal quantity;
    private BigDecimal minThreshold;
}

