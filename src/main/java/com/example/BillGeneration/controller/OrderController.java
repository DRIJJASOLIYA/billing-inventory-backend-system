package com.example.BillGeneration.controller;

import com.example.BillGeneration.dto.OrderDetailsResponse;
import com.example.BillGeneration.dto.OrderRequest;
import com.example.BillGeneration.dto.OrderResponse;
import com.example.BillGeneration.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public OrderResponse placeOrder(
            @Valid @RequestBody OrderRequest request,
            @RequestHeader(name = "X-Idempotency-Key", required = false) String idempotencyKey
    ) {
        return submitOrder(request, idempotencyKey);
    }

    @GetMapping("/{id}")
    public OrderDetailsResponse getOrder(@PathVariable Long id) {
        return fetchOrder(id);
    }

    private OrderResponse submitOrder(OrderRequest request, String idempotencyKey) {
        return orderService.placeOrder(request, idempotencyKey);
    }

    private OrderDetailsResponse fetchOrder(Long id) {
        return orderService.getOrder(id);
    }
}
