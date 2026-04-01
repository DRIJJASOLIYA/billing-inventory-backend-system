package com.example.BillGeneration.service;

import com.example.BillGeneration.entity.Product;
import com.example.BillGeneration.exception.BadRequestException;
import com.example.BillGeneration.repository.OrderRepository;
import com.example.BillGeneration.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void updateStockShouldThrowWhenInsufficientStock() {
        Product product = new Product();
        product.setQuantity(2L);
        product.setThreshold(1L);

        assertThrows(BadRequestException.class, () -> productService.updateStock(product, 3L));
        verify(productRepository, never()).saveAndFlush(product);
    }

    @Test
    void updateQuantityShouldThrowWhenAdditionalQuantityIsNegative() {
        assertThrows(BadRequestException.class, () -> productService.updateQuantity(1L, -1L));
    }
}
