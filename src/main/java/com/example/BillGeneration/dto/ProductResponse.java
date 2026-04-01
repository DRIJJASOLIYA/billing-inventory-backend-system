package com.example.BillGeneration.dto;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String name,
        Long quantity,
        BigDecimal price,
        Long threshold
) {
}
