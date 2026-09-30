package com.larv.pharmacy.customer;

import com.larv.pharmacy.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Cached aggregates of the immutable customer ledger. The balance is only ever
 * modified inside a transaction that also appends a {@link CustomerTransaction},
 * so ledger and aggregates can never drift apart.
 *
 * <p>balance = totalPurchases - totalPaid - totalRefunds (+/- adjustments)</p>
 */
@Entity
@Table(name = "customer_accounts")
public class CustomerAccount extends BaseEntity {

    @Id
    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "total_purchases", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalPurchases = BigDecimal.ZERO;

    @Column(name = "total_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalPaid = BigDecimal.ZERO;

    @Column(name = "total_refunds", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalRefunds = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    protected CustomerAccount() {
    }

    public CustomerAccount(Long customerId) {
        this.customerId = customerId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public BigDecimal getTotalPurchases() {
        return totalPurchases;
    }

    public void setTotalPurchases(BigDecimal totalPurchases) {
        this.totalPurchases = totalPurchases;
    }

    public BigDecimal getTotalPaid() {
        return totalPaid;
    }

    public void setTotalPaid(BigDecimal totalPaid) {
        this.totalPaid = totalPaid;
    }

    public BigDecimal getTotalRefunds() {
        return totalRefunds;
    }

    public void setTotalRefunds(BigDecimal totalRefunds) {
        this.totalRefunds = totalRefunds;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
}
