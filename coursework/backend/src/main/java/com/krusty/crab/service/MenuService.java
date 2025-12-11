package com.krusty.crab.service;

import com.krusty.crab.entity.MenuItem;
import com.krusty.crab.exception.EntityNotFoundException;
import com.krusty.crab.exception.MenuException;
import com.krusty.crab.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MenuService {
    
    private final MenuItemRepository menuItemRepository;
    
    public List<MenuItem> getAllMenuItems() {
        return menuItemRepository.findAll();
    }
    
    public List<MenuItem> getAvailableMenuItems() {
        return menuItemRepository.findByAvailableTrue();
    }
    
    public MenuItem getMenuItemById(Integer id) {
        return menuItemRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("MenuItem", id));
    }
    
    public List<MenuItem> searchMenuItems(String name) {
        return menuItemRepository.findByNameContainingIgnoreCase(name);
    }
    
    @Transactional
    public MenuItem createMenuItem(MenuItem menuItem) {
        if (menuItem.getPrice() == null || menuItem.getPrice().signum() < 0) {
            throw new MenuException("Price must be non-negative");
        }
        MenuItem newItem = new MenuItem();
        newItem.setName(menuItem.getName());
        newItem.setDescription(menuItem.getDescription());
        newItem.setPrice(menuItem.getPrice());
        newItem.setAvailable(menuItem.getAvailable() != null ? menuItem.getAvailable() : true);
        newItem.setPrepTimeMinutes(menuItem.getPrepTimeMinutes() != null ? menuItem.getPrepTimeMinutes() : 0);
        try {
            MenuItem saved = menuItemRepository.save(newItem);
            log.info("MenuItem created with ID: {}", saved.getId());
            return saved;
        } catch (Exception e) {
            throw new MenuException("Failed to create menu item: " + e.getMessage(), e);
        }
    }
    
    @Transactional
    public MenuItem updateMenuItem(Integer id, MenuItem menuItemData) {
        MenuItem menuItem = getMenuItemById(id);
        if (menuItemData.getName() != null) {
            menuItem.setName(menuItemData.getName());
        }
        if (menuItemData.getDescription() != null) {
            menuItem.setDescription(menuItemData.getDescription());
        }
        if (menuItemData.getPrice() != null) {
            menuItem.setPrice(menuItemData.getPrice());
        }
        if (menuItemData.getAvailable() != null) {
            menuItem.setAvailable(menuItemData.getAvailable());
        }
        if (menuItemData.getPrepTimeMinutes() != null) {
            menuItem.setPrepTimeMinutes(menuItemData.getPrepTimeMinutes());
        }
        MenuItem updated = menuItemRepository.save(menuItem);
        log.info("MenuItem {} updated", id);
        return updated;
    }
    
    @Transactional
    public void deleteMenuItem(Integer id) {
        MenuItem menuItem = getMenuItemById(id);
        menuItemRepository.delete(menuItem);
        log.info("MenuItem {} deleted", id);
    }
}

