package com.example.BillGeneration.service;

import com.example.BillGeneration.entity.Product;
import com.example.BillGeneration.exception.BadRequestException;
import com.example.BillGeneration.exception.ResourceNotFoundException;
import com.example.BillGeneration.repository.OrderRepository;
import com.example.BillGeneration.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public ProductService(ProductRepository productRepository, OrderRepository orderRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    // add product
    @CacheEvict(cacheNames = {"productById", "allProducts", "lowStockProducts", "productPages"}, allEntries = true)
    public Product addProduct(Product product) {
        validateUniqueProductName(product.getName());
        return saveProduct(product);
    }

    @CacheEvict(cacheNames = {"productById", "allProducts", "lowStockProducts", "productPages"}, allEntries = true)
    public List<Product> addProducts(List<Product> products) {
        validateUniqueProductNames(products);
        return saveProducts(products);
    }

    @Cacheable(cacheNames = "allProducts")
    public List<Product> getAllProducts() {
        return fetchAllProducts();
    }

    @Cacheable(cacheNames = "lowStockProducts")
    public List<Product> getLowStockProducts() {
        return fetchLowStockProducts();
    }

    @Cacheable(cacheNames = "productPages", key = "#pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<Product> getProducts(Pageable pageable) {
        return fetchProductsPage(pageable);
    }

    @Cacheable(cacheNames = "productById", key = "#id")
    public Product getProduct(Long id) {
        return findProductById(id);
    }

    @CacheEvict(cacheNames = {"productById", "allProducts", "lowStockProducts", "productPages"}, allEntries = true)
    public Product updateStock(Product product, Long quantity) {
        validateStockUpdateRequest(product, quantity);
        Long remainingStock = calculateRemainingStock(product, quantity);
        persistRemainingStock(product, remainingStock);
        logLowStockIfNeeded(product, remainingStock);
        return product;
    }

    @CacheEvict(cacheNames = {"productById", "allProducts", "lowStockProducts", "productPages"}, allEntries = true)
    public Product updateProduct(Long productId, Product updatedProduct) {
        Product existingProduct = getProduct(productId);
        validateUniqueProductNameForUpdate(updatedProduct.getName(), productId);
        applyProductUpdate(existingProduct, updatedProduct);
        return saveProduct(existingProduct);
    }

    @CacheEvict(cacheNames = {"productById", "allProducts", "lowStockProducts", "productPages"}, allEntries = true)
    public Product updateQuantity(Long productId, Long quantity) {
        validateAdditionalQuantity(quantity);
        Product product = getProduct(productId);
        Long updatedQuantity = calculateUpdatedQuantity(product.getQuantity(), quantity);
        product.setQuantity(updatedQuantity);
        return saveProduct(product);
    }

    @CacheEvict(cacheNames = {"productById", "allProducts", "lowStockProducts", "productPages"}, allEntries = true)
    public void deleteProduct(Long productId) {
        Product product = getProduct(productId);
        validateProductDeletion(product.getId());
        productRepository.delete(product);
    }

    public Map<Long, Product> getProductsByIds(Collection<Long> ids) {
        List<Product> products = productRepository.findByIdIn(ids);
        Map<Long, Product> productsById = new HashMap<>();
        for (Product product : products) {
            productsById.put(product.getId(), product);
        }
        for (Long id : ids) {
            if (!productsById.containsKey(id)) {
                throw new ResourceNotFoundException("Product not found for id: " + id);
            }
        }
        return productsById;
    }

    private Product saveProduct(Product product) {
        return productRepository.save(product);
    }

    private List<Product> saveProducts(List<Product> products) {
        return productRepository.saveAll(products);
    }

    private List<Product> fetchAllProducts() {
        return productRepository.findAll();
    }

    private List<Product> fetchLowStockProducts() {
        return productRepository.findLowStockProducts();
    }

    private Page<Product> fetchProductsPage(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    private Product findProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found for id: " + id));
    }

    private void validateStockUpdateRequest(Product product, Long quantity) {
        validateRequestedQuantity(quantity);
        validateStockFields(product);
        validateSufficientStock(product.getQuantity(), quantity);
    }

    private void validateRequestedQuantity(Long quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BadRequestException("Quantity must be greater than zero");
        }
    }

    private void validateStockFields(Product product) {
        if (product.getQuantity() == null) {
            throw new BadRequestException("Product quantity is not initialized");
        }
        if (product.getThreshold() == null) {
            throw new BadRequestException("Product threshold is not initialized");
        }
    }

    private void validateSufficientStock(Long availableQuantity, Long requestedQuantity) {
        if (availableQuantity < requestedQuantity) {
            throw new BadRequestException("Insufficient stock");
        }
    }

    private Long calculateRemainingStock(Product product, Long quantity) {
        return product.getQuantity() - quantity;
    }

    private void persistRemainingStock(Product product, Long remainingStock) {
        product.setQuantity(remainingStock);
        productRepository.save(product);
    }

    private void logLowStockIfNeeded(Product product, Long remainingStock) {
        if (remainingStock <= product.getThreshold()) {
            log.info("ALERT! STOCK LOW FOR PRODUCT {}", product.getName());
        }
    }

    private void validateAdditionalQuantity(Long quantity) {
        if (quantity == null || quantity < 0) {
            throw new BadRequestException("Quantity must be zero or greater");
        }
    }

    private Long calculateUpdatedQuantity(Long currentQuantity, Long additionalQuantity) {
        Long current = currentQuantity == null ? 0L : currentQuantity;
        return current + additionalQuantity;
    }

    private void applyProductUpdate(Product existingProduct, Product updatedProduct) {
        existingProduct.setName(updatedProduct.getName());
        existingProduct.setQuantity(updatedProduct.getQuantity());
        existingProduct.setPrice(updatedProduct.getPrice());
        existingProduct.setThreshold(updatedProduct.getThreshold());
    }

    private void validateProductDeletion(Long productId) {
        if (orderRepository.existsByProductId(productId)) {
            throw new BadRequestException("Product cannot be deleted because it is referenced by existing orders");
        }
    }

    private void validateUniqueProductName(String name) {
        String normalizedName = normalizeName(name);
        if (productRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new BadRequestException("Product with the same name already exists");
        }
    }

    private void validateUniqueProductNames(List<Product> products) {
        Set<String> namesInRequest = new HashSet<>();
        for (Product product : products) {
            String normalizedName = normalizeName(product.getName());
            if (!namesInRequest.add(normalizedName)) {
                throw new BadRequestException("Bulk request contains duplicate product names");
            }
            if (productRepository.existsByNameIgnoreCase(normalizedName)) {
                throw new BadRequestException("Product with the same name already exists: " + normalizedName);
            }
        }
    }

    private void validateUniqueProductNameForUpdate(String name, Long productId) {
        String normalizedName = normalizeName(name);
        if (productRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, productId)) {
            throw new BadRequestException("Product with the same name already exists");
        }
    }

    private String normalizeName(String name) {
        return name == null ? null : name.trim();
    }
}
