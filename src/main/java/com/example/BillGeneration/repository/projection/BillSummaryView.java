package com.example.BillGeneration.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface BillSummaryView {

    Long getBillId();

    String getBillNo();

    LocalDate getBillDate();

    String getCustomerName();

    BigDecimal getFinalAmount();

    Long getItemCount();
}
