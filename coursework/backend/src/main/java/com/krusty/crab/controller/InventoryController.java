package com.krusty.crab.controller;

import com.krusty.crab.api.InventoryApi;
import com.krusty.crab.dto.generated.InventoryUpdateRequest;
import com.krusty.crab.dto.generated.LowStockItem;
import com.krusty.crab.mapper.InventoryMapper;
import com.krusty.crab.mapper.InventoryTransactionMapper;
import com.krusty.crab.security.UserPrincipal;
import com.krusty.crab.service.InventoryService;
import com.krusty.crab.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('Manager')")
public class InventoryController implements InventoryApi {

    private final InventoryService inventoryService;
    private final InventoryMapper inventoryMapper;
    private final InventoryTransactionMapper inventoryTransactionMapper;

    @Override
    public ResponseEntity<List<com.krusty.crab.dto.generated.InventoryRecord>> getInventory(Boolean lowStock, Double thresholdFactor) {
        log.info("Getting inventory, lowStock: {}, thresholdFactor: {}", lowStock, thresholdFactor);
        if (Boolean.TRUE.equals(lowStock)) {
            List<LowStockItem> lowStockItems = inventoryService.getLowStock(thresholdFactor);
            List<com.krusty.crab.entity.InventoryRecord> records = lowStockItems.stream()
                .map(item -> inventoryService.getInventoryRecordByIngredientId(item.getIngredientId()))
                .collect(java.util.stream.Collectors.toList());
            return ResponseEntity.ok(inventoryMapper.toDtoList(records));
        } else {
            List<com.krusty.crab.entity.InventoryRecord> records = inventoryService.getAllInventoryRecords();
            return ResponseEntity.ok(inventoryMapper.toDtoList(records));
        }
    }

    @Override
    public ResponseEntity<List<com.krusty.crab.dto.generated.LowStockItem>> getLowStock(Double thresholdFactor) {
        log.info("Getting low stock items with thresholdFactor: {}", thresholdFactor);
        List<com.krusty.crab.dto.generated.LowStockItem> items = inventoryService.getLowStock(thresholdFactor);
        return ResponseEntity.ok(items);
    }

    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.InventoryRecord> updateInventory(Integer ingredientId, InventoryUpdateRequest inventoryUpdateRequest) {
        UserPrincipal user = SecurityUtil.getCurrentUser();
        log.info("Updating inventory for ingredient: {} with delta: {}", ingredientId, inventoryUpdateRequest.getDelta());
        com.krusty.crab.entity.InventoryRecord record = inventoryService.updateInventory(
            ingredientId,
            inventoryUpdateRequest.getDelta(),
            user.getUserId(),
            inventoryUpdateRequest.getReason()
        );
        com.krusty.crab.dto.generated.InventoryRecord dto = inventoryMapper.toDto(record);
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<List<com.krusty.crab.dto.generated.InventoryTransaction>> getInventoryTransactions(
        Integer ingredientId,
        Integer limit,
        Integer offset
    ) {
        log.info(
            "Getting inventory transactions, ingredientId: {}, limit: {}, offset: {}",
            ingredientId,
            limit,
            offset
        );
        List<com.krusty.crab.entity.InventoryTransaction> transactions =
            inventoryService.getInventoryTransactions(ingredientId, limit, offset);
        return ResponseEntity.ok(inventoryTransactionMapper.toDtoList(transactions));
    }
}
