package com.example.BillGeneration.repository;

import com.example.BillGeneration.entity.Product;
import com.example.BillGeneration.repository.projection.ProductStockView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @Query("select p.name as name, p.quantity as quantity, p.threshold as threshold from Product p")
    List<ProductStockView> findAllStockViews();

    @Query("select p from Product p where p.quantity <= p.threshold")
    List<Product> findLowStockProducts();

    List<Product> findByIdIn(Collection<Long> ids);
}
