package com.example.BillGeneration.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderResponse {

    private Long orderId;
    private String customerName;
    private BigDecimal finalAmount;
    private String orderStatus;
    private String paymentStatus;
    private String idempotencyKey;
    private String message;
}
