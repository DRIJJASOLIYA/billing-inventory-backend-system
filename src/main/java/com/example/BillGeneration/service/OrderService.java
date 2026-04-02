package com.example.BillGeneration.service;

import com.example.BillGeneration.config.AppProperties;
import com.example.BillGeneration.dto.OrderDetailsResponse;
import com.example.BillGeneration.dto.OrderItemRequest;
import com.example.BillGeneration.dto.OrderItemResponse;
import com.example.BillGeneration.dto.OrderRequest;
import com.example.BillGeneration.dto.OrderResponse;
import com.example.BillGeneration.dto.PageResponse;
import com.example.BillGeneration.entity.Bill;
import com.example.BillGeneration.entity.BillItem;
import com.example.BillGeneration.entity.OrderDetails;
import com.example.BillGeneration.entity.OrderItem;
import com.example.BillGeneration.entity.Product;
import com.example.BillGeneration.exception.BadRequestException;
import com.example.BillGeneration.exception.ResourceNotFoundException;
import com.example.BillGeneration.repository.BillRepository;
import com.example.BillGeneration.repository.OrderRepository;
import com.example.BillGeneration.repository.projection.OrderSummaryView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final String PAYMENT_PENDING = "PENDING";
    private static final String PAYMENT_SUCCESS = "SUCCESS";
    private static final String PAYMENT_FAILED = "FAILED";
    private static final String ORDER_PENDING = "PENDING";
    private static final String ORDER_PLACED = "PLACED";
    private static final String ORDER_PAYMENT_FAILED = "PAYMENT_FAILED";
    private static final String BILL_PREFIX = "BILL-";
    private static final String ENTITY_ORDER = "ORDER";
    private static final String ENTITY_PRODUCT = "PRODUCT";
    private static final String ENTITY_NOTIFICATION = "NOTIFICATION";

    private final ProductService productService;
    private final OrderRepository orderRepository;
    private final NotificationService notificationService;
    private final BillRepository billRepository;
    private final AuditLogService auditLogService;
    private final String adminEmail;
    private final AppProperties appProperties;
    private final int mockFailEveryNOrders;

    public OrderService(
            ProductService productService,
            OrderRepository orderRepository,
            NotificationService notificationService,
            BillRepository billRepository,
            AuditLogService auditLogService,
            @Value("${admin.email}") String adminEmail,
            @Value("${app.mock-payment.fail-every-n-orders:0}") int mockFailEveryNOrders,
            AppProperties appProperties
    ) {
        this.productService = productService;
        this.orderRepository = orderRepository;
        this.notificationService = notificationService;
        this.billRepository = billRepository;
        this.auditLogService = auditLogService;
        this.adminEmail = adminEmail;
        this.mockFailEveryNOrders = mockFailEveryNOrders;
        this.appProperties = appProperties;
    }

    @Transactional
    public OrderResponse placeOrder(OrderRequest request, String idempotencyKey) {
        OrderDetails existingOrder = resolveExistingIdempotentOrder(idempotencyKey);
        if (existingOrder != null) {
            auditLogService.record(ENTITY_ORDER, existingOrder.getId(), "IDEMPOTENT_REPLAY", "SUCCESS",
                    "Existing order returned for idempotency key");
            return buildOrderResponse(existingOrder, existingOrder.getPaymentStatus());
        }

        List<RequestedItem> requestedItems = fetchAndValidateProducts(request.getItems());
        OrderAmounts amounts = calculateOrderAmounts(requestedItems);
        OrderDetails order = createPendingOrder(request, requestedItems, amounts, idempotencyKey);
        auditLogService.record(ENTITY_ORDER, order.getId(), "ORDER_CREATED", "SUCCESS",
                "Order created with " + order.getItems().size() + " items");

        String paymentStatus = resolvePaymentStatus(order);
        markPaymentStatus(order, paymentStatus);

        if (isPaymentSuccessful(paymentStatus)) {
            processSuccessfulPayment(order);
        } else {
            order.setOrderStatus(ORDER_PAYMENT_FAILED);
            saveOrder(order);
            auditLogService.record(ENTITY_ORDER, order.getId(), "PAYMENT", "FAILED", "Payment marked as failed");
        }
        return buildOrderResponse(order, paymentStatus);
    }

    @Transactional(readOnly = true)
    public OrderDetailsResponse getOrder(Long orderId) {
        OrderDetails order = orderRepository.findWithProductAndBillById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found for id: " + orderId));
        return mapOrder(order);
    }

    @Transactional(readOnly = true)
    public List<OrderDetailsResponse> getOrders() {
        return mapOrderSummaries(orderRepository.findOrderSummaries());
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderDetailsResponse> getOrdersPage(String customerName, String paymentStatus, String orderStatus, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<OrderSummaryView> result = orderRepository.findOrderSummaries(customerName, paymentStatus, orderStatus, pageable);
        List<OrderDetailsResponse> items = mapOrderSummaries(result.getContent());
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

    private OrderDetails resolveExistingIdempotentOrder(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return orderRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
    }

    private List<RequestedItem> fetchAndValidateProducts(List<OrderItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new BadRequestException("Order items must not be empty");
        }

        Map<Long, MergedItem> mergedItems = new LinkedHashMap<>();
        for (OrderItemRequest item : items) {
            validateRequestedQuantity(item.getQuantity());
            BigDecimal discountAmount = normalizeDiscount(item.getDiscountAmount());
            mergedItems.merge(
                    item.getProductId(),
                    new MergedItem(item.getQuantity(), discountAmount),
                    (left, right) -> new MergedItem(
                            left.quantity() + right.quantity(),
                            left.discountAmount().add(right.discountAmount()).setScale(2, RoundingMode.HALF_UP)
                    )
            );
        }

        List<RequestedItem> requestedItems = new ArrayList<>();
        Map<Long, Product> productsById = productService.getProductsByIds(mergedItems.keySet());
        for (Map.Entry<Long, MergedItem> entry : mergedItems.entrySet()) {
            Product product = productsById.get(entry.getKey());
            validateProductForOrder(product);
            requestedItems.add(buildRequestedItem(product, entry.getValue()));
        }
        return requestedItems;
    }

    private RequestedItem buildRequestedItem(Product product, MergedItem mergedItem) {
        BigDecimal grossAmount = product.getPrice()
                .multiply(BigDecimal.valueOf(mergedItem.quantity()))
                .setScale(2, RoundingMode.HALF_UP);
        if (mergedItem.discountAmount().compareTo(grossAmount) > 0) {
            throw new BadRequestException("Discount cannot exceed line amount for product id: " + product.getId());
        }
        BigDecimal taxableAmount = grossAmount.subtract(mergedItem.discountAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxAmount = taxableAmount.multiply(resolveGstRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal lineTotal = taxableAmount.add(taxAmount).setScale(2, RoundingMode.HALF_UP);
        return new RequestedItem(product, mergedItem.quantity(), mergedItem.discountAmount(), taxAmount, taxableAmount, lineTotal);
    }

    private OrderAmounts calculateOrderAmounts(List<RequestedItem> requestedItems) {
        BigDecimal total = requestedItems.stream()
                .map(RequestedItem::taxableAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal gst = requestedItems.stream()
                .map(RequestedItem::taxAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal finalAmount = requestedItems.stream()
                .map(RequestedItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        return new OrderAmounts(total, gst, finalAmount);
    }

    private OrderDetails createPendingOrder(
            OrderRequest request,
            List<RequestedItem> requestedItems,
            OrderAmounts amounts,
            String idempotencyKey
    ) {
        OrderDetails order = new OrderDetails();
        order.setCustomerName(request.getCustomerName());
        order.setMobileNo(request.getMobileNo());
        order.setTotalAmount(amounts.total());
        order.setGst(amounts.gst());
        order.setFinalAmount(amounts.finalAmount());
        order.setOrderStatus(ORDER_PENDING);
        order.setPaymentStatus(PAYMENT_PENDING);
        order.setIdempotencyKey(normalizeIdempotencyKey(idempotencyKey));
        order.setItems(createOrderItems(order, requestedItems));
        return saveOrder(order);
    }

    private List<OrderItem> createOrderItems(OrderDetails order, List<RequestedItem> requestedItems) {
        List<OrderItem> items = new ArrayList<>();
        for (RequestedItem requestedItem : requestedItems) {
            items.add(createOrderItem(order, requestedItem));
        }
        return items;
    }

    private OrderItem createOrderItem(OrderDetails order, RequestedItem requestedItem) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(requestedItem.product());
        item.setProductName(requestedItem.product().getName());
        item.setQuantity(requestedItem.quantity());
        item.setPriceAtTime(requestedItem.product().getPrice());
        item.setDiscountAmount(requestedItem.discountAmount());
        item.setTaxAmount(requestedItem.taxAmount());
        item.setLineTotal(requestedItem.lineTotal());
        return item;
    }

    private String resolvePaymentStatus(OrderDetails order) {
        if (mockFailEveryNOrders > 0 && order.getId() % mockFailEveryNOrders == 0) {
            return PAYMENT_FAILED;
        }
        return PAYMENT_SUCCESS;
    }

    private void markPaymentStatus(OrderDetails order, String paymentStatus) {
        order.setPaymentStatus(paymentStatus);
    }

    private boolean isPaymentSuccessful(String paymentStatus) {
        return PAYMENT_SUCCESS.equals(paymentStatus);
    }

    private void processSuccessfulPayment(OrderDetails order) {
        updateStockAndSendLowStockAlert(order);
        Bill savedBill = createAndSaveBill(order);
        order.setBill(savedBill);
        order.setOrderStatus(ORDER_PLACED);
        saveOrder(order);
        auditLogService.record(ENTITY_ORDER, order.getId(), "PAYMENT", "SUCCESS", "Payment completed and order placed");
        String message = createOrderConfirmationMessage(order);
        sendOrderNotificationsAfterCommit(order, message);
    }

    private void updateStockAndSendLowStockAlert(OrderDetails order) {
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            Product updatedProduct = productService.updateStock(product, item.getQuantity());
            auditLogService.record(ENTITY_PRODUCT, product.getId(), "STOCK_ADJUSTED", "SUCCESS",
                    "Reduced stock by " + item.getQuantity() + " for order " + order.getId());
            sendLowStockAlertIfNeeded(updatedProduct);
        }
    }

    private void sendLowStockAlertIfNeeded(Product product) {
        if (product.getQuantity() <= product.getThreshold()) {
            log.info("LOW STOCK ALERT FOR {}", product.getName());
            sendLowStockEmailSafely(product);
        }
    }

    private Bill createAndSaveBill(OrderDetails order) {
        Bill bill = createBill(order);
        for (OrderItem orderItem : order.getItems()) {
            bill.getItems().add(createBillItem(bill, orderItem));
        }
        return billRepository.save(bill);
    }

    private Bill createBill(OrderDetails order) {
        Bill bill = new Bill();
        bill.setBillNo(BILL_PREFIX + System.currentTimeMillis());
        bill.setBillDate(LocalDate.now());
        bill.setCustomerName(order.getCustomerName());
        bill.setFinalAmount(order.getFinalAmount());
        return bill;
    }

    private BillItem createBillItem(Bill bill, OrderItem orderItem) {
        BillItem item = new BillItem();
        item.setBill(bill);
        item.setProduct(orderItem.getProduct());
        item.setProductName(orderItem.getProductName());
        item.setQuantity(orderItem.getQuantity());
        item.setPriceAtTime(orderItem.getPriceAtTime());
        item.setDiscountAmount(orderItem.getDiscountAmount());
        item.setTaxAmount(orderItem.getTaxAmount());
        item.setLineTotal(orderItem.getLineTotal());
        return item;
    }

    private String createOrderConfirmationMessage(OrderDetails order) {
        return String.format(
                appProperties.getMessages().getOrderSmsTemplate(),
                order.getCustomerName(),
                order.getId(),
                order.getBill() != null ? order.getBill().getBillNo() : "N/A",
                order.getItems().size(),
                order.getPaymentStatus(),
                order.getFinalAmount().toPlainString()
        );
    }

    private OrderDetails saveOrder(OrderDetails order) {
        return orderRepository.save(order);
    }

    private OrderResponse buildOrderResponse(OrderDetails order, String paymentStatus) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerName(),
                order.getFinalAmount(),
                order.getOrderStatus(),
                order.getPaymentStatus(),
                order.getIdempotencyKey(),
                isPaymentSuccessful(paymentStatus)
                        ? appProperties.getMessages().getOrderSuccessText()
                        : appProperties.getMessages().getOrderFailureText()
        );
    }

    private void validateProductForOrder(Product product) {
        if (product.getPrice() == null) {
            throw new BadRequestException("Product price is not configured");
        }
        if (product.getQuantity() == null) {
            throw new BadRequestException("Product quantity is not configured");
        }
        if (product.getThreshold() == null) {
            throw new BadRequestException("Product threshold is not configured");
        }
    }

    private void validateRequestedQuantity(Long quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BadRequestException("Quantity must be greater than zero");
        }
    }

    private BigDecimal normalizeDiscount(BigDecimal discountAmount) {
        if (discountAmount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (discountAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Discount amount cannot be negative");
        }
        return discountAmount.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return idempotencyKey.trim();
    }

    private void sendLowStockEmailSafely(Product product) {
        if (adminEmail == null || adminEmail.isBlank()) {
            log.warn("Skipping low stock email because admin.email is not configured");
            return;
        }
        runAfterCommit(() -> dispatchNotifications("low stock email", List.of(trackLowStockEmailNotification(
                product,
                adminEmail,
                notificationService.sendEmail(
                        adminEmail,
                        appProperties.getMessages().getLowStockSubject(),
                        createLowStockMessage(product)
                )
        ))));
    }

    private void sendOrderNotificationsAfterCommit(OrderDetails order, String message) {
        runAfterCommit(() -> dispatchOrderNotifications(order, message));
    }

    private void dispatchOrderNotifications(OrderDetails order, String message) {
        List<CompletableFuture<Void>> notifications = new ArrayList<>();
        if (notificationService.isSmsConfigured()) {
            notifications.add(trackNotification("SMS", order, notificationService.sendSms(order.getMobileNo(), message), order.getMobileNo()));
        } else {
            log.warn("Skipping SMS notification because twilio.from.number is not configured");
        }
        if (notificationService.isWhatsAppConfigured()) {
            notifications.add(trackNotification("WHATSAPP", order, notificationService.sendWhatsApp(order.getMobileNo(), message), order.getMobileNo()));
        } else {
            log.info("Skipping WhatsApp notification because twilio.whatsapp.from is not configured");
        }
        dispatchNotifications("order notifications", notifications);
    }

    private CompletableFuture<Void> trackNotification(
            String channel,
            OrderDetails order,
            CompletableFuture<Void> future,
            String recipient
    ) {
        return future.whenComplete((ignored, throwable) -> {
            if (throwable == null) {
                auditLogService.record(ENTITY_NOTIFICATION, order.getId(), channel + "_SENT", "SUCCESS",
                        channel + " notification sent to " + recipient);
                return;
            }
            Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                    ? throwable.getCause()
                    : throwable;
            auditLogService.record(ENTITY_NOTIFICATION, order.getId(), channel + "_SENT", "FAILED",
                    channel + " notification failed for " + recipient + ": " + cause.getMessage());
        });
    }

    private CompletableFuture<Void> trackLowStockEmailNotification(
            Product product,
            String recipient,
            CompletableFuture<Void> future
    ) {
        return future.whenComplete((ignored, throwable) -> {
            if (throwable == null) {
                auditLogService.record(ENTITY_NOTIFICATION, product.getId(), "LOW_STOCK_EMAIL_SENT", "SUCCESS",
                        "Low stock email sent to " + recipient + " for product " + product.getName());
                return;
            }
            Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                    ? throwable.getCause()
                    : throwable;
            auditLogService.record(ENTITY_NOTIFICATION, product.getId(), "LOW_STOCK_EMAIL_SENT", "FAILED",
                    "Low stock email failed for " + recipient + ": " + cause.getMessage());
        });
    }

    private void dispatchNotifications(String context, List<CompletableFuture<Void>> notifications) {
        if (notifications.isEmpty()) {
            return;
        }
        CompletableFuture.allOf(notifications.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, throwable) -> {
                    if (throwable instanceof CompletionException completionException) {
                        RuntimeException failure = unwrapNotificationFailure(completionException);
                        log.warn("Failed to deliver {}", context, failure);
                    } else if (throwable != null) {
                        log.warn("Failed to deliver {}", context, throwable);
                    }
                });
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private RuntimeException unwrapNotificationFailure(CompletionException ex) {
        if (ex.getCause() instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new RuntimeException("Notification delivery failed", ex.getCause());
    }

    private BigDecimal resolveGstRate() {
        return appProperties.getBilling().getGstRate();
    }

    private String createLowStockMessage(Product product) {
        return String.format(
                appProperties.getMessages().getLowStockTemplate(),
                product.getName(),
                product.getQuantity(),
                product.getThreshold()
        );
    }

    private OrderDetailsResponse mapOrder(OrderDetails order) {
        Long billId = order.getBill() != null ? order.getBill().getId() : null;
        String billNo = order.getBill() != null ? order.getBill().getBillNo() : null;
        return new OrderDetailsResponse(
                order.getId(),
                order.getCustomerName(),
                order.getMobileNo(),
                order.getItems().stream().map(this::mapOrderItem).toList(),
                order.getTotalAmount(),
                order.getGst(),
                order.getFinalAmount(),
                order.getOrderStatus(),
                order.getPaymentStatus(),
                order.getIdempotencyKey(),
                billId,
                billNo
        );
    }

    private OrderItemResponse mapOrderItem(OrderItem item) {
        Long productId = item.getProduct() != null ? item.getProduct().getId() : null;
        return new OrderItemResponse(
                productId,
                item.getProductName(),
                item.getQuantity(),
                item.getPriceAtTime(),
                item.getDiscountAmount(),
                item.getTaxAmount(),
                item.getLineTotal()
        );
    }

    private List<OrderDetailsResponse> mapOrderSummaries(List<OrderSummaryView> summaries) {
        if (summaries.isEmpty()) {
            return List.of();
        }
        List<OrderDetailsResponse> responses = new ArrayList<>(summaries.size());
        for (OrderSummaryView summary : summaries) {
            responses.add(new OrderDetailsResponse(
                    summary.getOrderId(),
                    summary.getCustomerName(),
                    summary.getMobileNo(),
                    List.of(),
                    summary.getTotalAmount(),
                    summary.getGst(),
                    summary.getFinalAmount(),
                    summary.getOrderStatus(),
                    summary.getPaymentStatus(),
                    summary.getIdempotencyKey(),
                    summary.getBillId(),
                    summary.getBillNo()
            ));
        }
        return responses;
    }

    private record MergedItem(Long quantity, BigDecimal discountAmount) {
    }

    private record RequestedItem(
            Product product,
            Long quantity,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal taxableAmount,
            BigDecimal lineTotal
    ) {
    }

    private record OrderAmounts(BigDecimal total, BigDecimal gst, BigDecimal finalAmount) {
    }
}
