package com.example.BillGeneration.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "bill_item", indexes = {
        @Index(name = "idx_bill_item_bill_id", columnList = "bill_id"),
        @Index(name = "idx_bill_item_product_id", columnList = "product_id")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BillItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id")
    private Bill bill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    private String productName;

    private Long quantity;

    private BigDecimal priceAtTime;

    private BigDecimal discountAmount;

    private BigDecimal taxAmount;

    private BigDecimal lineTotal;
}
