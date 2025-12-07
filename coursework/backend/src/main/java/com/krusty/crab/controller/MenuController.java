package com.krusty.crab.controller;

import com.krusty.crab.api.MenuApi;
import com.krusty.crab.dto.generated.MenuItemCreateRequest;
import com.krusty.crab.entity.MenuItem;
import com.krusty.crab.mapper.MenuMapper;
import com.krusty.crab.service.MenuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class MenuController implements MenuApi {
    
    private final MenuService menuService;
    private final MenuMapper menuMapper;
    
    @Override
    public ResponseEntity<List<com.krusty.crab.dto.generated.MenuItem>> getMenu(Boolean available, String search) {
        log.info("Getting menu, available: {}, search: {}", available, search);
        List<MenuItem> items;
        if (Boolean.TRUE.equals(available)) {
            items = menuService.getAvailableMenuItems();
        } else if (search != null && !search.isEmpty()) {
            items = menuService.searchMenuItems(search);
        } else {
            items = menuService.getAllMenuItems();
        }
        return ResponseEntity.ok(menuMapper.toDtoList(items));
    }
    
    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.MenuItem> getMenuItemById(Integer menuItemId) {
        log.info("Getting menu item by ID: {}", menuItemId);
        MenuItem item = menuService.getMenuItemById(menuItemId);
        com.krusty.crab.dto.generated.MenuItem dto = menuMapper.toDto(item);
        return ResponseEntity.ok(dto);
    }
    
    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.MenuItem> createMenuItem(MenuItemCreateRequest menuItemCreateRequest) {
        log.info("Creating menu item: {}", menuItemCreateRequest.getName());
        MenuItem item = menuMapper.toEntity(menuItemCreateRequest);
        MenuItem saved = menuService.createMenuItem(item);
        com.krusty.crab.dto.generated.MenuItem dto = menuMapper.toDto(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
    
    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.MenuItem> updateMenuItem(Integer menuItemId, MenuItemCreateRequest menuItemCreateRequest) {
        log.info("Updating menu item with ID: {}", menuItemId);
        MenuItem item = menuService.getMenuItemById(menuItemId);
        menuMapper.updateEntityFromRequest(menuItemCreateRequest, item);
        MenuItem updated = menuService.updateMenuItem(menuItemId, item);
        com.krusty.crab.dto.generated.MenuItem dto = menuMapper.toDto(updated);
        return ResponseEntity.ok(dto);
    }
    
    @Override
    public ResponseEntity<Void> deleteMenuItem(Integer menuItemId) {
        log.info("Deleting menu item with ID: {}", menuItemId);
        menuService.deleteMenuItem(menuItemId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}

