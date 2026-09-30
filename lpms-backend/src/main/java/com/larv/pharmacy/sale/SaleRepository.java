package com.larv.pharmacy.sale;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long>, JpaSpecificationExecutor<Sale> {

    /** Row lock preventing concurrent refunds of the same sale. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id")
    Optional<Sale> findWithLockById(@Param("id") Long id);

    @Query("select s from Sale s left join fetch s.customer left join fetch s.items where s.id = :id")
    Optional<Sale> findByIdWithItems(@Param("id") Long id);

    @Query("select s from Sale s left join fetch s.customer where s.id = :id")
    Optional<Sale> findByIdWithCustomer(@Param("id") Long id);

    /**
     * Revenue/cost/count of sales created in {@code [from, to)}, net of the
     * refunds attached to those sales (accrual basis on the sale date).
     */
    @Query("""
            select new com.larv.pharmacy.report.ProfitAggregateRow(
                count(s),
                coalesce(sum(s.total - s.refundedTotal), 0),
                coalesce(sum(s.costTotal - s.refundedCost), 0))
            from Sale s
            where s.createdAt >= :from and s.createdAt < :to
              and (:customerId is null or s.customer.id = :customerId)
              and (:paymentMethod is null or s.paymentMethod = :paymentMethod)
              and (:employeeId is null or s.createdBy = :employeeId)
            """)
    com.larv.pharmacy.report.ProfitAggregateRow aggregateProfit(
            @Param("from") java.time.Instant from,
            @Param("to") java.time.Instant to,
            @Param("customerId") Long customerId,
            @Param("paymentMethod") com.larv.pharmacy.common.domain.PaymentMethod paymentMethod,
            @Param("employeeId") Long employeeId);
}
