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
public class TopMenuItem {
    private Integer menuItemId;
    private String name;
    private Long quantity;
    private BigDecimal revenue;
}

