package com.larv.pharmacy.customer;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface CustomerAccountRepository extends JpaRepository<CustomerAccount, Long> {

    /** Row lock that serializes all balance changes for one customer. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query(
            "select a from CustomerAccount a where a.customerId = :id")
    Optional<CustomerAccount> findWithLockById(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(a.balance), 0) from CustomerAccount a")
    java.math.BigDecimal sumBalance();
}
