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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@Slf4j
public class MenuController implements MenuApi {
    
    private final MenuService menuService;
    private final MenuMapper menuMapper;
    
    @Override
    public ResponseEntity<List<com.krusty.crab.dto.generated.MenuItem>> getMenu(Boolean available, String search) {
        log.info("Getting menu, available: {}, search: {}", available, search);
        boolean requestedAvailableOnly = Boolean.TRUE.equals(available);
        List<MenuItem> items;
        if (requestedAvailableOnly) {
            items = menuService.getAvailableMenuItems();
        } else if (search != null && !search.isEmpty()) {
            items = menuService.searchMenuItems(search);
        } else {
            items = menuService.getAllMenuItems();
        }

        List<com.krusty.crab.dto.generated.MenuItem> dtoList = menuMapper.toDtoList(items);
        List<Integer> menuItemIds = dtoList.stream()
            .map(com.krusty.crab.dto.generated.MenuItem::getId)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toList());

        Set<Integer> outOfStockIds = menuService.getOutOfStockMenuItemIds(menuItemIds);
        for (com.krusty.crab.dto.generated.MenuItem dto : dtoList) {
            Integer id = dto.getId();
            if (id != null && outOfStockIds.contains(id) && Boolean.TRUE.equals(dto.getAvailable())) {
                dto.setAvailable(false);
            }
        }

        if (requestedAvailableOnly) {
            dtoList = dtoList.stream()
                .filter(dto -> Boolean.TRUE.equals(dto.getAvailable()))
                .collect(java.util.stream.Collectors.toList());
        }

        return ResponseEntity.ok(dtoList);
    }
    
    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.MenuItem> getMenuItemById(Integer menuItemId) {
        log.info("Getting menu item by ID: {}", menuItemId);
        MenuItem item = menuService.getMenuItemById(menuItemId);
        com.krusty.crab.dto.generated.MenuItem dto = menuMapper.toDto(item);
        if (menuItemId != null && Boolean.TRUE.equals(dto.getAvailable())) {
            Set<Integer> outOfStockIds = menuService.getOutOfStockMenuItemIds(java.util.List.of(menuItemId));
            if (outOfStockIds.contains(menuItemId)) {
                dto.setAvailable(false);
            }
        }
        return ResponseEntity.ok(dto);
    }
    
    @Override
    @PreAuthorize("hasRole('Manager')")
    public ResponseEntity<com.krusty.crab.dto.generated.MenuItem> createMenuItem(MenuItemCreateRequest menuItemCreateRequest) {
        log.info("Creating menu item: {}", menuItemCreateRequest.getName());
        MenuItem item = menuMapper.toEntity(menuItemCreateRequest);
        MenuItem saved = menuService.createMenuItem(item);
        com.krusty.crab.dto.generated.MenuItem dto = menuMapper.toDto(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
    
    @Override
    @PreAuthorize("hasRole('Manager')")
    public ResponseEntity<com.krusty.crab.dto.generated.MenuItem> updateMenuItem(Integer menuItemId, MenuItemCreateRequest menuItemCreateRequest) {
        log.info("Updating menu item with ID: {}", menuItemId);
        MenuItem item = menuService.getMenuItemById(menuItemId);
        menuMapper.updateEntityFromRequest(menuItemCreateRequest, item);
        MenuItem updated = menuService.updateMenuItem(menuItemId, item);
        com.krusty.crab.dto.generated.MenuItem dto = menuMapper.toDto(updated);
        return ResponseEntity.ok(dto);
    }
    
    @Override
    @PreAuthorize("hasRole('Manager')")
    public ResponseEntity<Void> deleteMenuItem(Integer menuItemId) {
        log.info("Deleting menu item with ID: {}", menuItemId);
        menuService.deleteMenuItem(menuItemId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
