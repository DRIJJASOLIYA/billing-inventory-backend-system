package com.example.BillGeneration.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderDetailsResponse {

    private Long orderId;
    private String customerName;
    private String mobileNo;
    private List<OrderItemResponse> items;
    private BigDecimal totalAmount;
    private BigDecimal gst;
    private BigDecimal finalAmount;
    private String orderStatus;
    private String paymentStatus;
    private String idempotencyKey;
    private Long billId;
    private String billNo;
}
