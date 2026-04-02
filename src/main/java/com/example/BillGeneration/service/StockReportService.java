package com.example.BillGeneration.service;

import com.example.BillGeneration.repository.OrderRepository;
import com.example.BillGeneration.repository.ProductRepository;
import com.example.BillGeneration.repository.projection.OrderItemView;
import com.example.BillGeneration.repository.projection.OrderSummaryView;
import com.example.BillGeneration.repository.projection.ProductStockView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StockReportService {

    private static final Logger log = LoggerFactory.getLogger(StockReportService.class);
    private static final String STOCK_REPORT_HEADER = "product_name,remaining_stock,threshold\n";

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final BillService billService;
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final String adminEmail;

    public StockReportService(
            ProductRepository productRepository,
            OrderRepository orderRepository,
            BillService billService,
            EmailService emailService,
            AuditLogService auditLogService,
            @Value("${admin.email}") String adminEmail
    ) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.billService = billService;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
        this.adminEmail = adminEmail;
    }

    @Scheduled(cron = "${stock.report.cron:0 0 0 * * *}")
    public void sendDailyStockReport() {
        if (adminEmail == null || adminEmail.isBlank()) {
            log.warn("Skipping stock report email because admin.email is not configured");
            return;
        }
        sendAttachmentEmail(
                "Daily Stock Report",
                "Please find the daily stock report attached.",
                buildReportFileName("stock-report"),
                generateStockReportCsv()
        );
        sendAttachmentEmail(
                "Daily Order Report",
                "Please find the daily order report attached.",
                buildReportFileName("order-report"),
                generateOrderReportCsv()
        );
        sendAttachmentEmail(
                "Daily Bill Report",
                "Please find the daily bill report attached.",
                buildReportFileName("bill-report"),
                billService.generateBillsCsv()
        );
    }

    public String generateStockReportCsv() {
        List<ProductStockView> products = productRepository.findAllStockViews();
        StringBuilder csv = new StringBuilder();
        csv.append(STOCK_REPORT_HEADER);
        for (ProductStockView product : products) {
            csv.append(safeCsv(product.getName())).append(",")
                    .append(valueOrEmpty(product.getQuantity())).append(",")
                    .append(valueOrEmpty(product.getThreshold())).append("\n");
        }
        return csv.toString();
    }

    public String generateOrderReportCsv() {
        StringBuilder csv = new StringBuilder();
        csv.append("order_id,customer_name,mobile_no,order_status,payment_status,idempotency_key,total_amount,gst,final_amount,item_count\n");
        List<OrderSummaryView> orders = orderRepository.findOrderSummaries();
        Map<Long, List<OrderItemView>> itemsByOrderId = groupOrderItems(orders);
        for (OrderSummaryView order : orders) {
            csv.append(valueOrEmpty(order.getOrderId())).append(",")
                    .append(safeCsv(order.getCustomerName())).append(",")
                    .append(safeCsv(order.getMobileNo())).append(",")
                    .append(safeCsv(order.getOrderStatus())).append(",")
                    .append(safeCsv(order.getPaymentStatus())).append(",")
                    .append(safeCsv(order.getIdempotencyKey())).append(",")
                    .append(valueOrEmpty(order.getTotalAmount())).append(",")
                    .append(valueOrEmpty(order.getGst())).append(",")
                    .append(valueOrEmpty(order.getFinalAmount())).append(",")
                    .append(valueOrEmpty(order.getItemCount())).append("\n");

            for (OrderItemView item : itemsByOrderId.getOrDefault(order.getOrderId(), List.of())) {
                csv.append(",,ITEM,")
                        .append(safeCsv(item.getProductName())).append(",")
                        .append(valueOrEmpty(item.getQuantity())).append(",")
                        .append(valueOrEmpty(item.getPriceAtTime())).append(",")
                        .append(valueOrEmpty(item.getDiscountAmount())).append(",")
                        .append(valueOrEmpty(item.getTaxAmount())).append(",")
                        .append(valueOrEmpty(item.getLineTotal())).append(",\n");
            }
        }
        return csv.toString();
    }

    private Map<Long, List<OrderItemView>> groupOrderItems(List<OrderSummaryView> orders) {
        if (orders.isEmpty()) {
            return Map.of();
        }
        List<Long> orderIds = orders.stream()
                .map(OrderSummaryView::getOrderId)
                .toList();
        Map<Long, List<OrderItemView>> itemsByOrderId = new LinkedHashMap<>();
        for (OrderItemView item : orderRepository.findOrderItemsByOrderIds(orderIds)) {
            itemsByOrderId.computeIfAbsent(item.getOrderId(), ignored -> new ArrayList<>()).add(item);
        }
        return itemsByOrderId;
    }

    private void sendAttachmentEmail(String subject, String body, String fileName, String csvReport) {
        emailService.sendMailWithAttachment(
                adminEmail,
                subject,
                body,
                fileName,
                csvReport.getBytes(StandardCharsets.UTF_8)
        );
        auditLogService.record("REPORT", null, subject.replace(' ', '_').toUpperCase(), "SUCCESS",
                "Generated and emailed " + fileName);
    }

    private String buildReportFileName(String prefix) {
        return prefix + "-" + LocalDate.now() + ".csv";
    }

    private String valueOrEmpty(Object value) {
        return value == null ? "" : value.toString();
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
}
