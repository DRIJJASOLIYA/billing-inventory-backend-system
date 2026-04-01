package com.example.BillGeneration.repository;

import com.example.BillGeneration.entity.Bill;
import com.example.BillGeneration.repository.projection.BillItemView;
import com.example.BillGeneration.repository.projection.BillSummaryView;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BillRepository extends JpaRepository<Bill, Long>, JpaSpecificationExecutor<Bill> {

    @EntityGraph(attributePaths = {"items"})
    Optional<Bill> findWithItemsById(Long id);

    @EntityGraph(attributePaths = {"items"})
    Optional<Bill> findWithItemsByBillNo(String billNo);

    @EntityGraph(attributePaths = {"items"})
    List<Bill> findAllByOrderByIdDesc();

    @Query("""
            select
                b.id as billId,
                b.billNo as billNo,
                b.billDate as billDate,
                b.customerName as customerName,
                b.finalAmount as finalAmount,
                count(bi.id) as itemCount
            from Bill b
            left join b.items bi
            group by b.id, b.billNo, b.billDate, b.customerName, b.finalAmount
            order by b.id desc
            """)
    List<BillSummaryView> findBillSummaries();

    @Query(
            value = """
                    select
                        b.id as billId,
                        b.billNo as billNo,
                        b.billDate as billDate,
                        b.customerName as customerName,
                        b.finalAmount as finalAmount,
                        count(bi.id) as itemCount
                    from Bill b
                    left join b.items bi
                    where (:customerName is null or :customerName = '' or lower(b.customerName) like lower(concat('%', :customerName, '%')))
                      and (:billNo is null or :billNo = '' or lower(b.billNo) like lower(concat('%', :billNo, '%')))
                    group by b.id, b.billNo, b.billDate, b.customerName, b.finalAmount
                    order by b.id desc
                    """,
            countQuery = """
                    select count(b.id)
                    from Bill b
                    where (:customerName is null or :customerName = '' or lower(b.customerName) like lower(concat('%', :customerName, '%')))
                      and (:billNo is null or :billNo = '' or lower(b.billNo) like lower(concat('%', :billNo, '%')))
                    """
    )
    Page<BillSummaryView> findBillSummaries(
            @Param("customerName") String customerName,
            @Param("billNo") String billNo,
            Pageable pageable
    );

    @Query("""
            select
                bi.bill.id as billId,
                p.id as productId,
                bi.productName as productName,
                bi.quantity as quantity,
                bi.priceAtTime as priceAtTime,
                bi.discountAmount as discountAmount,
                bi.taxAmount as taxAmount,
                bi.lineTotal as lineTotal
            from BillItem bi
            left join bi.product p
            where bi.bill.id in :billIds
            order by bi.bill.id desc, bi.id asc
            """)
    List<BillItemView> findBillItemsByBillIds(@Param("billIds") List<Long> billIds);
}
