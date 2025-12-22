package com.krusty.crab.service;

import com.krusty.crab.dto.generated.LowStockItem;
import com.krusty.crab.entity.InventoryRecord;
import com.krusty.crab.entity.InventoryTransaction;
import com.krusty.crab.exception.EntityNotFoundException;
import com.krusty.crab.exception.InventoryException;
import com.krusty.crab.repository.IngredientRepository;
import com.krusty.crab.repository.InventoryRepository;
import com.krusty.crab.repository.InventoryTransactionRepository;
import com.krusty.crab.util.DbErrorUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final IngredientRepository ingredientRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;

    public List<LowStockItem> getLowStock(Double thresholdFactor) {
        List<Object[]> results = inventoryRepository.callLowStock(thresholdFactor);
        List<LowStockItem> items = new ArrayList<>();

        for (Object[] row : results) {
            LowStockItem item = new LowStockItem();
            item.setIngredientId(((Number) row[0]).intValue());
            item.setName((String) row[1]);
            item.setQuantity((BigDecimal) row[2]);
            item.setMinThreshold((BigDecimal) row[3]);
            items.add(item);
        }

        return items;
    }

    @Transactional
    public void adjustInventory(Integer ingredientId, Double delta, Integer employeeId, String reason) {
        ingredientRepository.findById(ingredientId)
            .orElseThrow(() -> new EntityNotFoundException("Ingredient", ingredientId));

        if (delta == null) {
            throw new InventoryException("Delta cannot be null");
        }

        try {
            inventoryRepository.callAdjustInventory(ingredientId, delta, reason, employeeId);
            log.info("Inventory adjusted for ingredient {} by {} (employeeId={}, reason={})", ingredientId, delta, employeeId, reason);
        } catch (DataAccessException e) {
            String dbMessage = DbErrorUtil.extractMeaningfulMessage(e);
            throw new InventoryException(dbMessage != null ? dbMessage : "Failed to adjust inventory: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new InventoryException("Failed to adjust inventory: " + e.getMessage(), e);
        }
    }

    public List<InventoryRecord> getAllInventoryRecords() {
        return inventoryRepository.findAll();
    }

    public InventoryRecord getInventoryRecordByIngredientId(Integer ingredientId) {
        return inventoryRepository.findByIngredientId(ingredientId)
            .orElseThrow(() -> new EntityNotFoundException("InventoryRecord", "ingredientId", ingredientId));
    }

    @Transactional
    public InventoryRecord updateInventory(Integer ingredientId, Double delta, Integer employeeId, String reason) {
        adjustInventory(ingredientId, delta, employeeId, reason);
        return getInventoryRecordByIngredientId(ingredientId);
    }

    public List<InventoryTransaction> getInventoryTransactions(Integer ingredientId, Integer limit, Integer offset) {
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

        return inventoryTransactionRepository.findRecent(ingredientId, resolvedLimit, resolvedOffset);
    }
}
