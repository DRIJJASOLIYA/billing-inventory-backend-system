package com.example.BillGeneration.controller;

import com.example.BillGeneration.exception.ApiExceptionHandler;
import com.example.BillGeneration.service.BillService;
import com.example.BillGeneration.service.OrderService;
import com.example.BillGeneration.service.ProductService;
import com.example.BillGeneration.service.StockReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import(ApiExceptionHandler.class)
class AdminControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @MockBean
    private OrderService orderService;

    @MockBean
    private BillService billService;

    @MockBean
    private StockReportService stockReportService;

    @Test
    void getProductsPageShouldReturnBadRequestWhenSizeIsZero() throws Exception {
        mockMvc.perform(get("/admin/products/page")
                        .param("page", "0")
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void addProductsShouldReturnBadRequestWhenListIsEmpty() throws Exception {
        mockMvc.perform(post("/admin/products/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
