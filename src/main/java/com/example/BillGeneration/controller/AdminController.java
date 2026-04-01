package com.example.BillGeneration.controller;

import com.example.BillGeneration.dto.AuditLogResponse;
import com.example.BillGeneration.dto.BillDetailsResponse;
import com.example.BillGeneration.dto.CreateProductRequest;
import com.example.BillGeneration.dto.OrderDetailsResponse;
import com.example.BillGeneration.dto.PageResponse;
import com.example.BillGeneration.dto.ProductResponse;
import com.example.BillGeneration.dto.UpdateQuantityRequest;
import com.example.BillGeneration.entity.Product;
import com.example.BillGeneration.service.AuditLogService;
import com.example.BillGeneration.service.BillService;
import com.example.BillGeneration.service.OrderService;
import com.example.BillGeneration.service.ProductService;
import com.example.BillGeneration.service.StockReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@Validated
public class AdminController {

    private final ProductService productService;
    private final OrderService orderService;
    private final BillService billService;
    private final StockReportService stockReportService;
    private final AuditLogService auditLogService;

    public AdminController(
            ProductService productService,
            OrderService orderService,
            BillService billService,
            StockReportService stockReportService,
            AuditLogService auditLogService
    ) {
        this.productService = productService;
        this.orderService = orderService;
        this.billService = billService;
        this.stockReportService = stockReportService;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/products")
    public ProductResponse addProduct(@Valid @RequestBody CreateProductRequest product) {
        return toProductResponse(saveProduct(product));
    }

    @PostMapping("/products/bulk")
    public List<ProductResponse> addProducts(@Valid @RequestBody @NotEmpty(message = "Products list must not be empty") List<@Valid CreateProductRequest> products) {
        return saveProducts(products).stream()
                .map(this::toProductResponse)
                .toList();
    }

    @GetMapping("/products")
    public List<ProductResponse> getAllProducts() {
        return fetchAllProducts().stream()
                .map(this::toProductResponse)
                .toList();
    }

    @GetMapping("/products/{id}")
    public ProductResponse getProduct(@PathVariable Long id) {
        return toProductResponse(fetchProduct(id));
    }

    @GetMapping("/products/page")
    public PageResponse<ProductResponse> getProductsPage(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be zero or greater") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be at least 1") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return toPageResponse(fetchProductsPage(pageable));
    }

    @PatchMapping("/products/{id}/quantity")
    public ProductResponse updateQuantity(@PathVariable Long id, @Valid @RequestBody UpdateQuantityRequest request) {
        return toProductResponse(increaseProductQuantity(id, request));
    }

    @PutMapping("/products/{id}")
    public ProductResponse updateProduct(@PathVariable Long id, @Valid @RequestBody CreateProductRequest request) {
        return toProductResponse(replaceProduct(id, request));
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        removeProduct(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/products/low-stock")
    public List<ProductResponse> getLowStockProducts() {
        return fetchLowStockProducts().stream()
                .map(this::toProductResponse)
                .toList();
    }

    @GetMapping("/orders")
    public List<OrderDetailsResponse> getOrders() {
        return fetchOrders();
    }

    @GetMapping("/orders/page")
    public PageResponse<OrderDetailsResponse> getOrdersPage(
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String orderStatus,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be zero or greater") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be at least 1") int size
    ) {
        return orderService.getOrdersPage(customerName, paymentStatus, orderStatus, page, size);
    }

    @GetMapping("/bills")
    public List<BillDetailsResponse> getBills() {
        return fetchBills();
    }

    @GetMapping("/bills/page")
    public PageResponse<BillDetailsResponse> getBillsPage(
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String billNo,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be zero or greater") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be at least 1") int size
    ) {
        return billService.getBillsPage(customerName, billNo, page, size);
    }

    @GetMapping(value = "/reports/stock", produces = "text/csv")
    public ResponseEntity<String> getStockReport() {
        return buildStockReportResponse();
    }

    @GetMapping(value = "/reports/orders", produces = "text/csv")
    public ResponseEntity<String> getOrderReport() {
        return buildCsvResponse(stockReportService.generateOrderReportCsv(), "order-report.csv");
    }

    @GetMapping(value = "/reports/bills", produces = "text/csv")
    public ResponseEntity<String> getBillReport() {
        return buildCsvResponse(billService.generateBillsCsv(), "bill-report.csv");
    }

    @GetMapping("/audit-logs/page")
    public PageResponse<AuditLogResponse> getAuditLogsPage(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be zero or greater") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be at least 1") int size
    ) {
        return auditLogService.getAuditLogs(entityType, eventType, page, size);
    }

    private Product saveProduct(CreateProductRequest request) {
        Product product = new Product();
        applyProductRequest(product, request);
        return productService.addProduct(product);
    }

    private List<Product> saveProducts(List<CreateProductRequest> requests) {
        List<Product> products = requests.stream()
                .map(this::mapToProduct)
                .toList();
        return productService.addProducts(products);
    }

    private Product mapToProduct(CreateProductRequest request) {
        Product product = new Product();
        applyProductRequest(product, request);
        return product;
    }

    private void applyProductRequest(Product product, CreateProductRequest request) {
        product.setName(request.getName());
        product.setQuantity(request.getQuantity());
        product.setPrice(request.getPrice());
        product.setThreshold(request.getThreshold());
    }

    private List<Product> fetchAllProducts() {
        return productService.getAllProducts();
    }

    private Product fetchProduct(Long id) {
        return productService.getProduct(id);
    }

    private Page<Product> fetchProductsPage(Pageable pageable) {
        return productService.getProducts(pageable);
    }

    private Product increaseProductQuantity(Long id, UpdateQuantityRequest request) {
        return productService.updateQuantity(id, request.getQuantity());
    }

    private Product replaceProduct(Long id, CreateProductRequest request) {
        return productService.updateProduct(id, mapToProduct(request));
    }

    private void removeProduct(Long id) {
        productService.deleteProduct(id);
    }

    private List<Product> fetchLowStockProducts() {
        return productService.getLowStockProducts();
    }

    private List<OrderDetailsResponse> fetchOrders() {
        return orderService.getOrders();
    }

    private List<BillDetailsResponse> fetchBills() {
        return billService.getBills();
    }

    private ResponseEntity<String> buildStockReportResponse() {
        return buildCsvResponse(stockReportService.generateStockReportCsv(), "stock-report.csv");
    }

    private ResponseEntity<String> buildCsvResponse(String csv, String fileName) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    private ProductResponse toProductResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getQuantity(),
                product.getPrice(),
                product.getThreshold()
        );
    }

    private PageResponse<ProductResponse> toPageResponse(Page<Product> page) {
        return new PageResponse<>(
                page.getContent().stream()
                        .map(this::toProductResponse)
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.hasNext(),
                page.hasPrevious()
        );
    }
}
