package com.larv.pharmacy.sale;

import com.larv.pharmacy.common.domain.PaymentMethod;
import com.larv.pharmacy.report.ProfitDetailRow;
import com.larv.pharmacy.report.TopDrugRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    /**
     * Detailed profit rows for sales created in {@code [from, to)}, net of any
     * refunds attached to those lines, with optional filters.
     */
    @Query("""
            select new com.larv.pharmacy.report.ProfitDetailRow(
                s.id, s.createdAt, si.drug.id, si.drugName, s.paymentMethod, s.createdBy,
                s.customer.id, s.customer.name,
                si.quantity - si.refundedQuantity,
                si.revenue - si.refundedAmount,
                si.costTotal - si.refundedCost)
            from SaleItem si
            join si.sale s
            left join si.drug d
            where s.createdAt >= :from and s.createdAt < :to
              and (:drugId is null or si.drug.id = :drugId)
              and (:categoryId is null or d.category.id = :categoryId)
              and (:employeeId is null or s.createdBy = :employeeId)
              and (:paymentMethod is null or s.paymentMethod = :paymentMethod)
              and (:customerId is null or s.customer.id = :customerId)
            """)
    Page<ProfitDetailRow> profitDetails(@Param("from") Instant from,
                                        @Param("to") Instant to,
                                        @Param("drugId") Long drugId,
                                        @Param("categoryId") Long categoryId,
                                        @Param("employeeId") Long employeeId,
                                        @Param("paymentMethod") PaymentMethod paymentMethod,
                                        @Param("customerId") Long customerId,
                                        Pageable pageable);

    /** Best-selling drugs in a period (net of refunds), limited by the page size. */
    @Query("""
            select new com.larv.pharmacy.report.TopDrugRow(si.drug.id, si.drugName,
                sum(si.quantity - si.refundedQuantity),
                sum(si.revenue - si.refundedAmount))
            from SaleItem si
            join si.sale s
            where s.createdAt >= :from and s.createdAt < :to
            group by si.drug.id, si.drugName
            order by sum(si.quantity - si.refundedQuantity) desc
            """)
    List<TopDrugRow> topSelling(@Param("from") Instant from,
                                @Param("to") Instant to,
                                Pageable pageable);
}
