package com.krusty.crab.service;

import com.krusty.crab.dto.generated.MenuItem;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MenuAvailabilityService {

    private final MenuService menuService;

    public List<MenuItem> applyStockAvailability(List<MenuItem> items, boolean availableOnly) {
        if (items == null || items.isEmpty()) {
            return items;
        }
        List<Integer> menuItemIds =
                items.stream().map(MenuItem::getId).filter(Objects::nonNull).toList();
        if (!menuItemIds.isEmpty()) {
            Set<Integer> outOfStockIds = menuService.getOutOfStockMenuItemIds(menuItemIds);
            if (!outOfStockIds.isEmpty()) {
                for (MenuItem item : items) {
                    Integer id = item.getId();
                    if (id != null && Boolean.TRUE.equals(item.getAvailable()) && outOfStockIds.contains(id)) {
                        item.setAvailable(false);
                    }
                }
            }
        }

        if (!availableOnly) {
            return items;
        }
        return items.stream()
                .filter(item -> Boolean.TRUE.equals(item.getAvailable()))
                .toList();
    }

    public MenuItem applyStockAvailability(MenuItem item) {
        if (item == null) {
            return null;
        }
        Integer id = item.getId();
        if (id == null || !Boolean.TRUE.equals(item.getAvailable())) {
            return item;
        }
        if (menuService.getOutOfStockMenuItemIds(List.of(id)).contains(id)) {
            item.setAvailable(false);
        }
        return item;
    }
}
