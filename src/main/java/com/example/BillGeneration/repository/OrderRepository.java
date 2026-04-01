package com.example.BillGeneration.repository;

import com.example.BillGeneration.entity.OrderDetails;
import com.example.BillGeneration.repository.projection.OrderItemView;
import com.example.BillGeneration.repository.projection.OrderSummaryView;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderDetails, Long>, JpaSpecificationExecutor<OrderDetails> {

    @Query("select count(oi) > 0 from OrderItem oi where oi.product.id = :productId")
    boolean existsByProductId(@Param("productId") Long productId);

    @EntityGraph(attributePaths = {"items", "items.product", "bill"})
    Optional<OrderDetails> findWithProductAndBillById(Long id);

    @EntityGraph(attributePaths = {"items", "items.product", "bill"})
    List<OrderDetails> findAllByOrderByIdDesc();

    Optional<OrderDetails> findByIdempotencyKey(String idempotencyKey);

    @Query("""
            select
                o.id as orderId,
                o.customerName as customerName,
                o.mobileNo as mobileNo,
                o.totalAmount as totalAmount,
                o.gst as gst,
                o.finalAmount as finalAmount,
                o.orderStatus as orderStatus,
                o.paymentStatus as paymentStatus,
                o.idempotencyKey as idempotencyKey,
                b.id as billId,
                b.billNo as billNo,
                count(oi.id) as itemCount
            from OrderDetails o
            left join o.bill b
            left join o.items oi
            group by o.id, o.customerName, o.mobileNo, o.totalAmount, o.gst, o.finalAmount,
                     o.orderStatus, o.paymentStatus, o.idempotencyKey, b.id, b.billNo
            order by o.id desc
            """)
    List<OrderSummaryView> findOrderSummaries();

    @Query(
            value = """
                    select
                        o.id as orderId,
                        o.customerName as customerName,
                        o.mobileNo as mobileNo,
                        o.totalAmount as totalAmount,
                        o.gst as gst,
                        o.finalAmount as finalAmount,
                        o.orderStatus as orderStatus,
                        o.paymentStatus as paymentStatus,
                        o.idempotencyKey as idempotencyKey,
                        b.id as billId,
                        b.billNo as billNo,
                        count(oi.id) as itemCount
                    from OrderDetails o
                    left join o.bill b
                    left join o.items oi
                    where (:customerName is null or :customerName = '' or lower(o.customerName) like lower(concat('%', :customerName, '%')))
                      and (:paymentStatus is null or :paymentStatus = '' or lower(o.paymentStatus) = lower(:paymentStatus))
                      and (:orderStatus is null or :orderStatus = '' or lower(o.orderStatus) = lower(:orderStatus))
                    group by o.id, o.customerName, o.mobileNo, o.totalAmount, o.gst, o.finalAmount,
                             o.orderStatus, o.paymentStatus, o.idempotencyKey, b.id, b.billNo
                    order by o.id desc
                    """,
            countQuery = """
                    select count(o.id)
                    from OrderDetails o
                    where (:customerName is null or :customerName = '' or lower(o.customerName) like lower(concat('%', :customerName, '%')))
                      and (:paymentStatus is null or :paymentStatus = '' or lower(o.paymentStatus) = lower(:paymentStatus))
                      and (:orderStatus is null or :orderStatus = '' or lower(o.orderStatus) = lower(:orderStatus))
                    """
    )
    Page<OrderSummaryView> findOrderSummaries(
            @Param("customerName") String customerName,
            @Param("paymentStatus") String paymentStatus,
            @Param("orderStatus") String orderStatus,
            Pageable pageable
    );

    @Query("""
            select
                oi.order.id as orderId,
                p.id as productId,
                oi.productName as productName,
                oi.quantity as quantity,
                oi.priceAtTime as priceAtTime,
                oi.discountAmount as discountAmount,
                oi.taxAmount as taxAmount,
                oi.lineTotal as lineTotal
            from OrderItem oi
            left join oi.product p
            where oi.order.id in :orderIds
            order by oi.order.id desc, oi.id asc
            """)
    List<OrderItemView> findOrderItemsByOrderIds(@Param("orderIds") List<Long> orderIds);
}
