package com.larv.pharmacy.customer;

import com.larv.pharmacy.common.domain.ImmutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Immutable entry of the customer financial ledger (append-only).
 * Ordering: ({@code customer_id}, {@code created_at}).
 */
@Entity
@Table(name = "customer_transactions", indexes = {
        @Index(name = "idx_customer_tx_customer_created", columnList = "customer_id, created_at"),
        @Index(name = "idx_customer_tx_reference", columnList = "reference_type, reference_id")
})
public class CustomerTransaction extends ImmutableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    /** Always a positive magnitude; direction carries the sign. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionDirection direction;

    /** Balance of the account immediately after this entry. */
    @Column(name = "balance_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "reference_type", length = 20)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(length = 255)
    private String description;

    @Column(name = "created_by")
    private Long createdBy;

    protected CustomerTransaction() {
    }

    public CustomerTransaction(Long customerId, TransactionType type, BigDecimal amount,
                               TransactionDirection direction, BigDecimal balanceAfter,
                               String referenceType, Long referenceId, String description, Long createdBy) {
        this.customerId = customerId;
        this.type = type;
        this.amount = amount;
        this.direction = direction;
        this.balanceAfter = balanceAfter;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.description = description;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionDirection getDirection() {
        return direction;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public String getDescription() {
        return description;
    }

    public Long getCreatedBy() {
        return createdBy;
    }
}
