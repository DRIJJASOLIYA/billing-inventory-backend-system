package com.example.BillGeneration.controller;

import com.example.BillGeneration.dto.BillDetailsResponse;
import com.example.BillGeneration.service.BillService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bills")
public class BillController {

    private final BillService billService;

    public BillController(BillService billService) {
        this.billService = billService;
    }

    @GetMapping("/{id}")
    public BillDetailsResponse getBill(@PathVariable Long id) {
        return billService.getBill(id);
    }

    @GetMapping("/number/{billNo}")
    public BillDetailsResponse getBillByBillNo(@PathVariable String billNo) {
        return billService.getBillByBillNo(billNo);
    }
}
