package com.larv.pharmacy.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface CustomerTransactionRepository extends JpaRepository<CustomerTransaction, Long> {

    @Query("""
            select t from CustomerTransaction t
            where t.customerId = :customerId
            order by t.createdAt asc, t.id asc
            """)
    Page<CustomerTransaction> history(@Param("customerId") Long customerId, Pageable pageable);

    @Query("select coalesce(sum(t.amount), 0) from CustomerTransaction t "
            + "where t.customerId = :customerId and t.direction = :direction")
    BigDecimal sumAmountByCustomerIdAndDirection(@Param("customerId") Long customerId,
                                                 @Param("direction") TransactionDirection direction);

    long countByCustomerId(Long customerId);

    @Query("select coalesce(sum(t.amount), 0) from CustomerTransaction t "
            + "where t.customerId = :customerId and t.type = :type")
    BigDecimal sumByCustomerIdAndType(@Param("customerId") Long customerId,
                                      @Param("type") TransactionType type);
}
