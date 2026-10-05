package com.syscrafters.salestorm.order.controller;

import com.syscrafters.salestorm.order.dto.OrderResponse;
import com.syscrafters.salestorm.order.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> get(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.get(orderId));
    }

    @PostMapping("/{orderId}/recover")
    public ResponseEntity<OrderResponse> recover(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.recover(orderId));
    }
}