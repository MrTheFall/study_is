package com.krusty.crab.service;

import com.krusty.crab.dto.LowStockItem;
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
    
    public List<LowStockItem> getLowStock(Double thresholdFactor) {
        List<Object[]> results = inventoryRepository.callLowStock(thresholdFactor);
        List<LowStockItem> items = new ArrayList<>();
        
        for (Object[] row : results) {
            LowStockItem item = LowStockItem.builder()
                .ingredientId(((Number) row[0]).intValue())
                .name((String) row[1])
                .quantity((BigDecimal) row[2])
                .minThreshold((BigDecimal) row[3])
                .build();
            items.add(item);
        }
        
        return items;
    }
    
    @Transactional
    public void restockIngredient(Integer ingredientId, Double delta) {
        inventoryRepository.callRestockIngredient(ingredientId, delta);
        log.info("Ingredient {} restocked by {}", ingredientId, delta);
    }
}

