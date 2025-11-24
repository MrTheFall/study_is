package com.krusty.crab.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KitchenQueueItem {
    private Integer orderId;
    private LocalDateTime createdAt;
    private String status;
    private List<OrderItemInfo> items;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItemInfo {
        private Integer menuItemId;
        private String name;
        private Integer quantity;
        private String note;
    }
}

