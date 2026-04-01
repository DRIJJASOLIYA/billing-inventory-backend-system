package com.example.BillGeneration.repository.projection;

import java.math.BigDecimal;

public interface BillItemView {

    Long getBillId();

    Long getProductId();

    String getProductName();

    Long getQuantity();

    BigDecimal getPriceAtTime();

    BigDecimal getDiscountAmount();

    BigDecimal getTaxAmount();

    BigDecimal getLineTotal();
}
