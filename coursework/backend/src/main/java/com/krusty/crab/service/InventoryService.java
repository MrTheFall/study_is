package com.krusty.crab.service;

import com.krusty.crab.dto.generated.LowStockItem;
import com.krusty.crab.entity.InventoryRecord;
import com.krusty.crab.exception.EntityNotFoundException;
import com.krusty.crab.exception.InventoryException;
import com.krusty.crab.repository.IngredientRepository;
import com.krusty.crab.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public void restockIngredient(Integer ingredientId, Double delta) {
        ingredientRepository.findById(ingredientId)
            .orElseThrow(() -> new EntityNotFoundException("Ingredient", ingredientId));
        
        if (delta == null) {
            throw new InventoryException("Delta cannot be null");
        }
        
        try {
            inventoryRepository.callRestockIngredient(ingredientId, delta);
            log.info("Ingredient {} restocked by {}", ingredientId, delta);
        } catch (Exception e) {
            throw new InventoryException("Failed to restock ingredient: " + e.getMessage(), e);
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
    public InventoryRecord updateInventory(Integer ingredientId, Double delta) {
        restockIngredient(ingredientId, delta);
        return getInventoryRecordByIngredientId(ingredientId);
    }
}

