package com.krusty.crab.dto;

import com.krusty.crab.entity.enums.OrderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceOrderRequest {
    private Integer clientId;
    private OrderType type;
    private String deliveryAddress;
    private List<OrderItemRequest> items;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItemRequest {
        private Integer menuItemId;
        private Integer quantity;
        private BigDecimal unitPrice; 
    }
}

