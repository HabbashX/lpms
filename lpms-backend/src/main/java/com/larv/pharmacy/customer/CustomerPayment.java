package com.larv.pharmacy.customer;

import com.larv.pharmacy.common.domain.BaseEntity;
import com.larv.pharmacy.common.domain.PaymentMethod;
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

/** A recorded debt payment. The matching PAYMENT ledger row references this row. */
@Entity
@Table(name = "customer_payments", indexes = {
        @Index(name = "idx_customer_payments_customer", columnList = "customer_id"),
        @Index(name = "idx_customer_payments_created", columnList = "created_at")
})
public class CustomerPayment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(length = 255)
    private String notes;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "transaction_id")
    private Long transactionId;

    protected CustomerPayment() {
    }

    public CustomerPayment(Long customerId, BigDecimal amount, PaymentMethod paymentMethod,
                           String notes, Long createdBy) {
        this.customerId = customerId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.notes = notes;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public String getNotes() {
        return notes;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }
}
