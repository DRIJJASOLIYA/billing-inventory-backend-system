package com.example.BillGeneration.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "order_details", indexes = {
        @Index(name = "idx_order_bill_id", columnList = "bill_id"),
        @Index(name = "idx_order_customer_name", columnList = "customerName"),
        @Index(name = "idx_order_payment_status", columnList = "paymentStatus"),
        @Index(name = "idx_order_order_status", columnList = "orderStatus"),
        @Index(name = "idx_order_idempotency_key", columnList = "idempotencyKey")
})
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
public class OrderDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String customerName;

    private String mobileNo;

    private BigDecimal totalAmount;

    private BigDecimal gst;

    private BigDecimal finalAmount;

    private String orderStatus;

    private String paymentStatus;

    @Column(unique = true)
    private String idempotencyKey;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id")
    private Bill bill;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

}
