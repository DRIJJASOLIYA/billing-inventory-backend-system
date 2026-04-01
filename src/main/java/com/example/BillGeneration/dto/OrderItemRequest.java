package com.example.BillGeneration.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderItemRequest {

    @NotNull
    @Min(value = 1, message = "Product id must be greater than zero")
    private Long productId;

    @NotNull
    @Min(value = 1, message = "Quantity must be at least 1")
    private Long quantity;

    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;
}
