package com.krusty.crab.controller;

import com.krusty.crab.api.OrdersApi;
import com.krusty.crab.dto.generated.PlaceOrder201Response;
import com.krusty.crab.dto.generated.PlaceOrderRequest;
import com.krusty.crab.dto.generated.UpdateOrderStatusRequest;
import com.krusty.crab.entity.Order;
import com.krusty.crab.mapper.OrderMapper;
import com.krusty.crab.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class OrdersController implements OrdersApi {
    
    private final OrderService orderService;
    private final OrderMapper orderMapper;
    
    @Override
    public ResponseEntity<PlaceOrder201Response> placeOrder(PlaceOrderRequest placeOrderRequest) {
        log.info("Placing order for client: {}", placeOrderRequest.getClientId());
        Integer orderId = orderService.placeOrder(placeOrderRequest);
        PlaceOrder201Response response = orderMapper.toPlaceOrderResponse(orderId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @Override
    public ResponseEntity<List<com.krusty.crab.dto.generated.Order>> getAllOrders(com.krusty.crab.dto.generated.OrderStatus status, Integer clientId) {
        log.info("Getting all orders, status: {}, clientId: {}", status, clientId);
        List<Order> orders;
        if (status != null && clientId != null) {
            orders = orderService.getOrdersByStatusAndClient(status.getValue(), clientId);
        } else if (status != null) {
            orders = orderService.getOrdersByStatus(status.getValue());
        } else if (clientId != null) {
            orders = orderService.getOrdersByClient(clientId);
        } else {
            orders = orderService.getAllOrders();
        }
        return ResponseEntity.ok(orderMapper.toDtoList(orders));
    }
    
    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.Order> getOrderById(Integer orderId) {
        log.info("Getting order by ID: {}", orderId);
        Order order = orderService.getOrderById(orderId);
        com.krusty.crab.dto.generated.Order dto = orderMapper.toDto(order);
        return ResponseEntity.ok(dto);
    }
    
    @Override
    public ResponseEntity<com.krusty.crab.dto.generated.Order> updateOrderStatus(Integer orderId, UpdateOrderStatusRequest updateOrderStatusRequest) {
        log.info("Updating order {} status to {}", orderId, updateOrderStatusRequest.getStatus());
        com.krusty.crab.entity.enums.OrderStatus newStatus = com.krusty.crab.entity.enums.OrderStatus.fromValue(updateOrderStatusRequest.getStatus().getValue());
        orderService.updateOrderStatus(orderId, newStatus);
        Order order = orderService.getOrderById(orderId);
        com.krusty.crab.dto.generated.Order dto = orderMapper.toDto(order);
        return ResponseEntity.ok(dto);
    }
}

