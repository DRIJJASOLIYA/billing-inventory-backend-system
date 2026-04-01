package com.example.BillGeneration.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BillDetailsResponse {

    private Long billId;
    private String billNo;
    private LocalDate billDate;
    private String customerName;
    private BigDecimal finalAmount;
    private List<BillItemResponse> items;
}
