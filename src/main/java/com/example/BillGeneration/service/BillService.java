package com.example.BillGeneration.service;

import com.example.BillGeneration.dto.BillDetailsResponse;
import com.example.BillGeneration.dto.BillItemResponse;
import com.example.BillGeneration.dto.PageResponse;
import com.example.BillGeneration.entity.Bill;
import com.example.BillGeneration.entity.BillItem;
import com.example.BillGeneration.exception.ResourceNotFoundException;
import com.example.BillGeneration.repository.BillRepository;
import com.example.BillGeneration.repository.projection.BillSummaryView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class BillService {

    private final BillRepository billRepository;

    public BillService(BillRepository billRepository) {
        this.billRepository = billRepository;
    }

    @Transactional(readOnly = true)
    public BillDetailsResponse getBill(Long id) {
        Bill bill = findBillById(id);
        return mapBill(bill);
    }

    @Transactional(readOnly = true)
    public BillDetailsResponse getBillByBillNo(String billNo) {
        Bill bill = billRepository.findWithItemsByBillNo(billNo)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found for bill number: " + billNo));
        return mapBill(bill);
    }

    @Transactional(readOnly = true)
    public List<BillDetailsResponse> getBills() {
        return mapBillSummaries(billRepository.findBillSummaries());
    }

    @Transactional(readOnly = true)
    public PageResponse<BillDetailsResponse> getBillsPage(String customerName, String billNo, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<BillSummaryView> result = billRepository.findBillSummaries(customerName, billNo, pageable);
        List<BillDetailsResponse> items = mapBillSummaries(result.getContent());
        return new PageResponse<>(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast(),
                result.hasNext(),
                result.hasPrevious()
        );
    }

    @Transactional(readOnly = true)
    public String generateBillsCsv() {
        StringBuilder csv = new StringBuilder();
        csv.append("bill_id,bill_no,bill_date,customer_name,final_amount,item_count\n");
        for (BillSummaryView bill : billRepository.findBillSummaries()) {
            csv.append(value(bill.getBillId())).append(",")
                    .append(safeCsv(bill.getBillNo())).append(",")
                    .append(value(bill.getBillDate())).append(",")
                    .append(safeCsv(bill.getCustomerName())).append(",")
                    .append(value(bill.getFinalAmount())).append(",")
                    .append(value(bill.getItemCount())).append("\n");
        }
        return csv.toString();
    }

    private Bill findBillById(Long id) {
        return billRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found for id: " + id));
    }

    private BillDetailsResponse mapBill(Bill bill) {
        List<BillItemResponse> items = bill.getItems().stream()
                .map(this::mapBillItem)
                .toList();
        return new BillDetailsResponse(
                bill.getId(),
                bill.getBillNo(),
                bill.getBillDate(),
                bill.getCustomerName(),
                bill.getFinalAmount(),
                items
        );
    }

    private BillItemResponse mapBillItem(BillItem item) {
        Long productId = item.getProduct() != null ? item.getProduct().getId() : null;
        return new BillItemResponse(
                productId,
                item.getProductName(),
                item.getQuantity(),
                item.getPriceAtTime(),
                item.getDiscountAmount(),
                item.getTaxAmount(),
                item.getLineTotal()
        );
    }

    private List<BillDetailsResponse> mapBillSummaries(List<BillSummaryView> summaries) {
        if (summaries.isEmpty()) {
            return List.of();
        }
        List<BillDetailsResponse> responses = new ArrayList<>(summaries.size());
        for (BillSummaryView summary : summaries) {
            responses.add(new BillDetailsResponse(
                    summary.getBillId(),
                    summary.getBillNo(),
                    summary.getBillDate(),
                    summary.getCustomerName(),
                    summary.getFinalAmount(),
                    List.of()
            ));
        }
        return responses;
    }

    private String safeCsv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }
}
