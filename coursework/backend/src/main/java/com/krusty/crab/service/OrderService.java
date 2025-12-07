package com.krusty.crab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.krusty.crab.dto.generated.KitchenQueueItem;
import com.krusty.crab.dto.generated.OrderItemInfo;
import com.krusty.crab.dto.generated.PlaceOrderRequest;
import com.krusty.crab.entity.Order;
import com.krusty.crab.entity.enums.OrderStatus;
import com.krusty.crab.exception.EntityNotFoundException;
import com.krusty.crab.exception.OrderException;
import com.krusty.crab.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;
    
    public OrderService(OrderRepository orderRepository, ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
    }
    
    @Transactional
    public Integer placeOrder(PlaceOrderRequest request) {
        try {
            List<Map<String, Object>> itemsJson = new ArrayList<>();
            for (com.krusty.crab.dto.generated.OrderItemRequest item : request.getItems()) {
                Map<String, Object> itemMap = new java.util.HashMap<>();
                itemMap.put("menu_item_id", item.getMenuItemId());
                itemMap.put("quantity", item.getQuantity());
                if (item.getUnitPrice() != null) {
                    itemMap.put("unit_price", item.getUnitPrice());
                }
                itemsJson.add(itemMap);
            }
            
            String itemsJsonb = objectMapper.writeValueAsString(itemsJson);
            
            Integer orderId = orderRepository.callPlaceOrder(
                request.getClientId(),
                request.getType() != null ? request.getType().getValue() : null,
                request.getDeliveryAddress(),
                itemsJsonb
            );
            
            log.info("Order placed successfully with ID: {}", orderId);
            return orderId;
        } catch (JsonProcessingException e) {
            log.error("Error converting items to JSON", e);
            throw new OrderException("Failed to convert order items to JSON", e);
        } catch (Exception e) {
            log.error("Error placing order", e);
            throw new OrderException("Failed to place order: " + e.getMessage(), e);
        }
    }
    
    @Transactional
    public void updateOrderStatus(Integer orderId, OrderStatus newStatus) {
        orderRepository.callUpdateOrderStatus(orderId, newStatus.getValue());
        log.info("Order {} status updated to {}", orderId, newStatus);
    }
    
    public List<KitchenQueueItem> getKitchenQueue() {
        List<Object[]> results = orderRepository.callGetKitchenQueue();
        List<KitchenQueueItem> queue = new ArrayList<>();
        
        for (Object[] row : results) {
            try {
                Integer orderId = (Integer) row[0];
                java.sql.Timestamp createdAt = (java.sql.Timestamp) row[1];
                String status = (String) row[2];
                String itemsJson = (String) row[3];
                
                List<OrderItemInfo> items = parseItemsJson(itemsJson);
                
                KitchenQueueItem item = new KitchenQueueItem();
                item.setOrderId(orderId);
                item.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime().atOffset(java.time.ZoneOffset.UTC) : null);
                item.setStatus(status);
                item.setItems(items);
                
                queue.add(item);
            } catch (Exception e) {
                log.error("Error parsing kitchen queue item", e);
            }
        }
        
        return queue;
    }
    
    private List<OrderItemInfo> parseItemsJson(String itemsJson) {
        try {
            if (itemsJson == null || itemsJson.trim().isEmpty() || itemsJson.equals("null")) {
                return new ArrayList<>();
            }
            
            List<Map<String, Object>> itemsList = objectMapper.readValue(
                itemsJson,
                new TypeReference<List<Map<String, Object>>>() {}
            );
            
            List<OrderItemInfo> items = new ArrayList<>();
            for (Map<String, Object> itemMap : itemsList) {
                OrderItemInfo itemInfo = new OrderItemInfo();
                itemInfo.setMenuItemId(((Number) itemMap.get("menu_item_id")).intValue());
                itemInfo.setName((String) itemMap.get("name"));
                itemInfo.setQuantity(((Number) itemMap.get("quantity")).intValue());
                itemInfo.setNote((String) itemMap.get("note"));
                items.add(itemInfo);
            }
            
            return items;
        } catch (JsonProcessingException e) {
            log.error("Error parsing items JSON: {}", itemsJson, e);
            return new ArrayList<>();
        }
    }
    
    public Order getOrderById(Integer orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new EntityNotFoundException("Order", orderId));
    }
}
