package com.krusty.crab.controller;

import com.krusty.crab.dto.KitchenQueueItem;
import com.krusty.crab.dto.LowStockItem;
import com.krusty.crab.dto.SalesSummary;
import com.krusty.crab.dto.TopMenuItem;
import com.krusty.crab.entity.Order;
import com.krusty.crab.service.AnalyticsService;
import com.krusty.crab.service.InventoryService;
import com.krusty.crab.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;


@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {
    
    private final OrderService orderService;
    private final AnalyticsService analyticsService;
    private final InventoryService inventoryService;
    
    @GetMapping("/kitchen-queue")
    public ResponseEntity<List<KitchenQueueItem>> getKitchenQueue() {
        List<KitchenQueueItem> queue = orderService.getKitchenQueue();
        return ResponseEntity.ok(queue);
    }
    
    @GetMapping("/order/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable Integer id) {
        try {
            Order order = orderService.getOrderById(id);
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/sales-summary")
    public ResponseEntity<SalesSummary> getSalesSummary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        
        LocalDateTime fromDate = from != null 
            ? LocalDateTime.parse(from) 
            : LocalDateTime.now().minusDays(30);
        LocalDateTime toDate = to != null 
            ? LocalDateTime.parse(to) 
            : LocalDateTime.now();
        
        SalesSummary summary = analyticsService.getSalesSummary(fromDate, toDate);
        return ResponseEntity.ok(summary);
    }
    
    @GetMapping("/top-menu-items")
    public ResponseEntity<List<TopMenuItem>> getTopMenuItems(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "10") Integer limit) {
        
        LocalDateTime fromDate = from != null 
            ? LocalDateTime.parse(from) 
            : LocalDateTime.now().minusDays(30);
        LocalDateTime toDate = to != null 
            ? LocalDateTime.parse(to) 
            : LocalDateTime.now();
        
        List<TopMenuItem> items = analyticsService.getTopMenuItems(fromDate, toDate, limit);
        return ResponseEntity.ok(items);
    }
    
    @GetMapping("/low-stock")
    public ResponseEntity<List<LowStockItem>> getLowStock(
            @RequestParam(defaultValue = "1.0") Double thresholdFactor) {
        List<LowStockItem> items = inventoryService.getLowStock(thresholdFactor);
        return ResponseEntity.ok(items);
    }
    
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK - All services are available");
    }
}

