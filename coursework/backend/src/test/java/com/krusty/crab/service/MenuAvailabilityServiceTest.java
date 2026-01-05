package com.krusty.crab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.krusty.crab.dto.generated.MenuItem;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MenuAvailabilityServiceTest {

    @Mock
    private MenuService menuService;

    @InjectMocks
    private MenuAvailabilityService menuAvailabilityService;

    @Test
    void applyStockAvailability_marksOutOfStockAndFilters() {
        MenuItem item1 = new MenuItem();
        item1.setId(1);
        item1.setAvailable(true);
        MenuItem item2 = new MenuItem();
        item2.setId(2);
        item2.setAvailable(true);
        MenuItem item3 = new MenuItem();
        item3.setAvailable(true);

        when(menuService.getOutOfStockMenuItemIds(List.of(1, 2))).thenReturn(Set.of(2));

        List<MenuItem> result =
                menuAvailabilityService.applyStockAvailability(List.of(item1, item2, item3), true);

        assertThat(item1.getAvailable()).isTrue();
        assertThat(item2.getAvailable()).isFalse();
        assertThat(result).containsExactly(item1, item3);
        verify(menuService).getOutOfStockMenuItemIds(List.of(1, 2));
    }

    @Test
    void applyStockAvailability_returnsInputWhenNullOrEmpty() {
        assertThat(menuAvailabilityService.applyStockAvailability(null, false)).isNull();
        assertThat(menuAvailabilityService.applyStockAvailability(List.of(), false)).isEmpty();
        verifyNoInteractions(menuService);
    }

    @Test
    void applyStockAvailability_updatesSingleItem() {
        MenuItem item = new MenuItem();
        item.setId(10);
        item.setAvailable(true);

        when(menuService.getOutOfStockMenuItemIds(List.of(10))).thenReturn(Set.of(10));

        MenuItem result = menuAvailabilityService.applyStockAvailability(item);

        assertThat(result.getAvailable()).isFalse();
    }

    @Test
    void applyStockAvailability_leavesUnavailableItemUnchanged() {
        MenuItem item = new MenuItem();
        item.setId(11);
        item.setAvailable(false);

        MenuItem result = menuAvailabilityService.applyStockAvailability(item);

        assertThat(result.getAvailable()).isFalse();
        verifyNoInteractions(menuService);
    }
}
