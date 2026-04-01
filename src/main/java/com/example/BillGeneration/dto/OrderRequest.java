package com.example.BillGeneration.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderRequest {

    @NotBlank
    private String customerName;

    @NotBlank
    @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Mobile number must be in E.164 format, e.g. +14155552671")
    private String mobileNo;

    @NotEmpty(message = "Order items must not be empty")
    private List<@Valid OrderItemRequest> items;
}
