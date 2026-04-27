package com.example.BillGeneration.controller;

import com.example.BillGeneration.dto.OrderResponse;
import com.example.BillGeneration.exception.ApiExceptionHandler;
import com.example.BillGeneration.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(ApiExceptionHandler.class)
class OrderControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Test
    void placeOrderShouldReturnUnsupportedMediaTypeWhenContentTypeIsMissing() throws Exception {
        String requestBody = """
                {
                  "customerName": "Header Missing",
                  "mobileNo": "+919157576177",
                  "items": [
                    {
                      "productId": 1,
                      "quantity": 1,
                      "discountAmount": 0
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/orders")
                        .header("X-Idempotency-Key", "idem-header-missing")
                        .content(requestBody))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.error").value("Content-Type must be application/json"));

        verifyNoInteractions(orderService);
    }

    @Test
    void placeOrderShouldReturnBadRequestWhenJsonIsMalformed() throws Exception {
        String malformedRequestBody = """
                {
                  "customerName": "Broken",
                  "mobileNo": "+919157576177",
                  "items": [
                    {
                      "productId": 1,
                      "quantity": 1
                    }
                  ]
                """;

        mockMvc.perform(post("/orders")
                        .header("X-Idempotency-Key", "idem-broken-json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedRequestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Malformed JSON request"));

        verifyNoInteractions(orderService);
    }

    @Test
    void placeOrderShouldSucceedForValidJsonWhenServiceAcceptsOrder() throws Exception {
        String requestBody = """
                {
                  "customerName": "Mock Failure",
                  "mobileNo": "+919157576177",
                  "items": [
                    {
                      "productId": 1,
                      "quantity": 1,
                      "discountAmount": 0
                    }
                  ]
                }
                """;

        when(orderService.placeOrder(any(), org.mockito.ArgumentMatchers.eq("idem-mock-failure"))).thenReturn(new OrderResponse(
                4L,
                "Mock Failure",
                new BigDecimal("141.59"),
                "PLACED",
                "SUCCESS",
                "idem-mock-failure",
                "Payment Successful. Order Placed Successfully"
        ));

        mockMvc.perform(post("/orders")
                        .header("X-Idempotency-Key", "idem-mock-failure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(4))
                .andExpect(jsonPath("$.idempotencyKey").value("idem-mock-failure"))
                .andExpect(jsonPath("$.paymentStatus").value("SUCCESS"));
    }

    @Test
    void placeOrderShouldReturnBadRequestWhenIdempotencyHeaderIsMissing() throws Exception {
        String requestBody = """
                {
                  "customerName": "Header Missing",
                  "mobileNo": "+919157576177",
                  "items": [
                    {
                      "productId": 1,
                      "quantity": 1,
                      "discountAmount": 0
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("X-Idempotency-Key header is required"));

        verifyNoInteractions(orderService);
    }

    @Test
    void placeOrderShouldReturnBadRequestWhenIdempotencyHeaderIsBlank() throws Exception {
        String requestBody = """
                {
                  "customerName": "Blank Header",
                  "mobileNo": "+919157576177",
                  "items": [
                    {
                      "productId": 1,
                      "quantity": 1,
                      "discountAmount": 0
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/orders")
                        .header("X-Idempotency-Key", "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("X-Idempotency-Key header is required"));

        verifyNoInteractions(orderService);
    }
}
