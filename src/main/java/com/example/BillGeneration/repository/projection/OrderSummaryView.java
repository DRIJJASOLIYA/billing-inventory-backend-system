package com.example.BillGeneration.repository.projection;

import java.math.BigDecimal;

public interface OrderSummaryView {

    Long getOrderId();

    String getCustomerName();

    String getMobileNo();

    BigDecimal getTotalAmount();

    BigDecimal getGst();

    BigDecimal getFinalAmount();

    String getOrderStatus();

    String getPaymentStatus();

    String getIdempotencyKey();

    Long getBillId();

    String getBillNo();

    Long getItemCount();
}
