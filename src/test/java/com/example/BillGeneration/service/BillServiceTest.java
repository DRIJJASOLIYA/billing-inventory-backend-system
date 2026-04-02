package com.example.BillGeneration.service;

import com.example.BillGeneration.dto.BillDetailsResponse;
import com.example.BillGeneration.repository.BillRepository;
import com.example.BillGeneration.repository.projection.BillSummaryView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillServiceTest {

    @Mock
    private BillRepository billRepository;

    @InjectMocks
    private BillService billService;

    @Test
    void getBillsShouldNotLoadItemsForSummaryResponse() {
        when(billRepository.findBillSummaries()).thenReturn(List.of(new BillSummaryView() {
            @Override
            public Long getBillId() {
                return 5L;
            }

            @Override
            public String getBillNo() {
                return "BILL-5";
            }

            @Override
            public LocalDate getBillDate() {
                return LocalDate.of(2026, 4, 2);
            }

            @Override
            public String getCustomerName() {
                return "Alice";
            }

            @Override
            public BigDecimal getFinalAmount() {
                return new BigDecimal("118.00");
            }

            @Override
            public Long getItemCount() {
                return 2L;
            }
        }));

        List<BillDetailsResponse> responses = billService.getBills();

        assertEquals(1, responses.size());
        assertTrue(responses.getFirst().getItems().isEmpty());
        verify(billRepository).findBillSummaries();
        verify(billRepository, never()).findBillItemsByBillIds(org.mockito.ArgumentMatchers.anyList());
    }
}
