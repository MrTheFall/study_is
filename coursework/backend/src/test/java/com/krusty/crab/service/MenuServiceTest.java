package com.krusty.crab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.krusty.crab.entity.MenuItem;
import com.krusty.crab.exception.EntityNotFoundException;
import com.krusty.crab.exception.MenuException;
import com.krusty.crab.repository.MenuItemRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

    @Mock
    private MenuItemRepository menuItemRepository;

    @InjectMocks
    private MenuService menuService;

    @Test
    void getOutOfStockMenuItemIds_returnsEmptyWhenNullOrEmpty() {
        assertThat(menuService.getOutOfStockMenuItemIds(null)).isEmpty();
        assertThat(menuService.getOutOfStockMenuItemIds(List.of())).isEmpty();
        verifyNoInteractions(menuItemRepository);
    }

    @Test
    void getOutOfStockMenuItemIds_returnsIdsFromRepository() {
        when(menuItemRepository.findOutOfStockMenuItemIds(List.of(1, 2))).thenReturn(List.of(2));

        Set<Integer> result = menuService.getOutOfStockMenuItemIds(List.of(1, 2));

        assertThat(result).containsExactly(2);
    }

    @Test
    void createMenuItem_rejectsNegativePrice() {
        MenuItem menuItem = new MenuItem();
        menuItem.setPrice(BigDecimal.valueOf(-1));

        assertThatThrownBy(() -> menuService.createMenuItem(menuItem)).isInstanceOf(MenuException.class);
    }

    @Test
    void createMenuItem_setsDefaultsAndSaves() {
        MenuItem menuItem = new MenuItem();
        menuItem.setName("Burger");
        menuItem.setDescription("Test item");
        menuItem.setPrice(BigDecimal.valueOf(7.50));

        MenuItem saved = new MenuItem();
        saved.setId(10);
        when(menuItemRepository.save(any(MenuItem.class))).thenReturn(saved);

        MenuItem result = menuService.createMenuItem(menuItem);

        assertThat(result).isSameAs(saved);
        ArgumentCaptor<MenuItem> captor = ArgumentCaptor.forClass(MenuItem.class);
        verify(menuItemRepository).save(captor.capture());
        MenuItem toSave = captor.getValue();
        assertThat(toSave.getAvailable()).isTrue();
        assertThat(toSave.getPrepTimeMinutes()).isEqualTo(0);
    }

    @Test
    void createMenuItem_wrapsRepositoryException() {
        MenuItem menuItem = new MenuItem();
        menuItem.setName("Burger");
        menuItem.setPrice(BigDecimal.valueOf(5));
        when(menuItemRepository.save(any(MenuItem.class))).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> menuService.createMenuItem(menuItem))
                .isInstanceOf(MenuException.class)
                .hasMessageContaining("Failed to create menu item");
    }

    @Test
    void updateMenuItem_updatesProvidedFields() {
        MenuItem existing = new MenuItem();
        existing.setId(1);
        existing.setName("Old");
        existing.setDescription("Old desc");
        existing.setPrice(BigDecimal.valueOf(5));
        existing.setAvailable(true);
        existing.setPrepTimeMinutes(5);

        MenuItem updates = new MenuItem();
        updates.setName("New");
        updates.setPrice(BigDecimal.valueOf(8));
        updates.setAvailable(false);
        updates.setPrepTimeMinutes(null);

        when(menuItemRepository.findById(1)).thenReturn(Optional.of(existing));
        when(menuItemRepository.save(existing)).thenReturn(existing);

        MenuItem result = menuService.updateMenuItem(1, updates);

        assertThat(result.getName()).isEqualTo("New");
        assertThat(result.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(8));
        assertThat(result.getAvailable()).isFalse();
        assertThat(result.getDescription()).isEqualTo("Old desc");
        assertThat(result.getPrepTimeMinutes()).isEqualTo(5);
    }

    @Test
    void deleteMenuItem_deletesExistingItem() {
        MenuItem existing = new MenuItem();
        existing.setId(2);

        when(menuItemRepository.findById(2)).thenReturn(Optional.of(existing));

        menuService.deleteMenuItem(2);

        verify(menuItemRepository).delete(existing);
    }

    @Test
    void getMenuItemById_throwsWhenMissing() {
        when(menuItemRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> menuService.getMenuItemById(99))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("MenuItem");
    }

    @Test
    void getAllMenuItems_returnsRepositoryData() {
        when(menuItemRepository.findAll()).thenReturn(List.of(new MenuItem()));

        assertThat(menuService.getAllMenuItems()).hasSize(1);
    }
}
