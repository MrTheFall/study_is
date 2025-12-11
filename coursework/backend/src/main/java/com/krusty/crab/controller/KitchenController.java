package com.krusty.crab.controller;

import com.krusty.crab.api.KitchenApi;
import com.krusty.crab.dto.generated.KitchenQueueItem;
import com.krusty.crab.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class KitchenController implements KitchenApi {
    
    private final OrderService orderService;
    
    @Override
    public ResponseEntity<List<KitchenQueueItem>> getKitchenQueue() {
        log.info("Getting kitchen queue");
        List<KitchenQueueItem> queue = orderService.getKitchenQueue();
        return ResponseEntity.ok(queue);
    }
}




