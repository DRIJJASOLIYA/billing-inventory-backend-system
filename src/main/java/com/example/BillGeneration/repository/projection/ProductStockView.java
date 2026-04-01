package com.example.BillGeneration.repository.projection;

public interface ProductStockView {
    String getName();
    Long getQuantity();
    Long getThreshold();
}
