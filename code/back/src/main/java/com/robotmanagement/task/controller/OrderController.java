package com.robotmanagement.task.controller;

import com.robotmanagement.common.api.PageResult;
import com.robotmanagement.task.dto.CreateOrderRequest;
import com.robotmanagement.task.dto.InstantActionRequest;
import com.robotmanagement.task.dto.InstantActionResponse;
import com.robotmanagement.task.dto.OrderResponse;
import com.robotmanagement.task.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/robots/{robotId}")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/orders")
    public OrderResponse createOrder(
        @PathVariable UUID robotId,
        @Valid @RequestBody CreateOrderRequest request
    ) {
        return orderService.createOrder(robotId, request);
    }

    @GetMapping("/orders")
    public PageResult<OrderResponse> listOrders(
        @PathVariable UUID robotId,
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size,
        @RequestParam(required = false) String status
    ) {
        return orderService.listOrders(robotId, page, size, status);
    }

    @GetMapping("/orders/{orderRecordId}")
    public OrderResponse getOrder(@PathVariable UUID robotId, @PathVariable UUID orderRecordId) {
        return orderService.getOrder(robotId, orderRecordId);
    }

    @PutMapping("/orders/{orderRecordId}/cancel")
    public OrderResponse cancelOrder(@PathVariable UUID robotId, @PathVariable UUID orderRecordId) {
        return orderService.cancelOrder(robotId, orderRecordId);
    }

    @PostMapping("/instant-actions")
    public InstantActionResponse createInstantAction(
        @PathVariable UUID robotId,
        @Valid @RequestBody InstantActionRequest request
    ) {
        return orderService.createInstantAction(robotId, request);
    }
}
