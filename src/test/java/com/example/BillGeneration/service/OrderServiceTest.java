package com.example.BillGeneration.service;

import com.example.BillGeneration.config.AppProperties;
import com.example.BillGeneration.dto.OrderItemRequest;
import com.example.BillGeneration.dto.OrderRequest;
import com.example.BillGeneration.dto.OrderResponse;
import com.example.BillGeneration.dto.OrderDetailsResponse;
import com.example.BillGeneration.entity.OrderDetails;
import com.example.BillGeneration.entity.Product;
import com.example.BillGeneration.repository.BillRepository;
import com.example.BillGeneration.repository.OrderRepository;
import com.example.BillGeneration.repository.projection.OrderSummaryView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private ProductService productService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private BillRepository billRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private AppProperties appProperties;

    @Test
    void placeOrderShouldReturnPaymentFailureWhenMockFailureIsEnabled() {
        AppProperties properties = new AppProperties();
        OrderService orderService = new OrderService(
                productService,
                orderRepository,
                notificationService,
                billRepository,
                auditLogService,
                "admin@example.com",
                1,
                properties
        );
        Product product = new Product();
        product.setId(1L);
        product.setName("Rice");
        product.setPrice(new BigDecimal("119.99"));
        product.setQuantity(10L);
        product.setThreshold(2L);

        OrderRequest request = new OrderRequest(
                "Mock Failure",
                "+919157576177",
                List.of(new OrderItemRequest(1L, 1L, BigDecimal.ZERO))
        );

        when(productService.getProductsByIds(any())).thenReturn(Map.of(1L, product));
        doAnswer(invocation -> {
            OrderDetails order = invocation.getArgument(0);
            if (order.getId() == null) {
                order.setId(1L);
            }
            return order;
        }).when(orderRepository).save(any(OrderDetails.class));

        OrderResponse response = orderService.placeOrder(request, null);

        assertEquals("PAYMENT_FAILED", response.getOrderStatus());
        assertEquals("FAILED", response.getPaymentStatus());
        assertEquals("Payment Failed. Please try again", response.getMessage());
        verify(billRepository, never()).save(any());
        verify(notificationService, never()).sendSms(any(), any());
        verify(notificationService, never()).sendWhatsApp(any(), any());
    }

    @Test
    void sendLowStockEmailSafelyShouldAuditSuccessfulEmail() throws Exception {
        AppProperties properties = new AppProperties();
        OrderService orderService = new OrderService(
                productService,
                orderRepository,
                notificationService,
                billRepository,
                auditLogService,
                "admin@example.com",
                0,
                properties
        );
        Product product = new Product();
        product.setId(7L);
        product.setName("Rice");
        product.setQuantity(2L);
        product.setThreshold(5L);

        when(notificationService.sendEmail(eq("admin@example.com"), eq(properties.getMessages().getLowStockSubject()), contains("Rice")))
                .thenReturn(CompletableFuture.completedFuture(null));

        Method method = OrderService.class.getDeclaredMethod("sendLowStockEmailSafely", Product.class);
        method.setAccessible(true);
        method.invoke(orderService, product);

        verify(auditLogService).record(
                "NOTIFICATION",
                7L,
                "LOW_STOCK_EMAIL_SENT",
                "SUCCESS",
                "Low stock email sent to admin@example.com for product Rice"
        );
    }

    @Test
    void getOrdersShouldNotLoadItemsForSummaryResponse() {
        OrderService orderService = new OrderService(
                productService,
                orderRepository,
                notificationService,
                billRepository,
                auditLogService,
                "admin@example.com",
                0,
                appProperties
        );

        when(orderRepository.findOrderSummaries()).thenReturn(List.of(new OrderSummaryView() {
            @Override
            public Long getOrderId() {
                return 1L;
            }

            @Override
            public String getCustomerName() {
                return "Alice";
            }

            @Override
            public String getMobileNo() {
                return "+911234567890";
            }

            @Override
            public BigDecimal getTotalAmount() {
                return new BigDecimal("100.00");
            }

            @Override
            public BigDecimal getGst() {
                return new BigDecimal("18.00");
            }

            @Override
            public BigDecimal getFinalAmount() {
                return new BigDecimal("118.00");
            }

            @Override
            public String getOrderStatus() {
                return "PLACED";
            }

            @Override
            public String getPaymentStatus() {
                return "SUCCESS";
            }

            @Override
            public String getIdempotencyKey() {
                return "idem-1";
            }

            @Override
            public Long getBillId() {
                return 10L;
            }

            @Override
            public String getBillNo() {
                return "BILL-1";
            }

            @Override
            public Long getItemCount() {
                return 2L;
            }
        }));

        List<OrderDetailsResponse> responses = orderService.getOrders();

        assertEquals(1, responses.size());
        assertTrue(responses.getFirst().getItems().isEmpty());
        verify(orderRepository).findOrderSummaries();
        verify(orderRepository, never()).findOrderItemsByOrderIds(org.mockito.ArgumentMatchers.anyList());
    }
}
