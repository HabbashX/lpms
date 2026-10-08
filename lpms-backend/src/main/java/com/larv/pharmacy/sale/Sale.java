package com.larv.pharmacy.sale;

import com.larv.pharmacy.common.domain.ImmutableEntity;
import com.larv.pharmacy.common.domain.PaymentMethod;
import com.larv.pharmacy.common.domain.TransferProvider;
import com.larv.pharmacy.customer.Customer;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A completed sale. All financial values are frozen at sale time:
 * {@code costTotal} reflects the weighted-average cost of the stock when the
 * sale happened and is never recalculated later.
 */
@Entity
@Table(name = "sales", indexes = {
        @Index(name = "idx_sales_created_at", columnList = "created_at"),
        @Index(name = "idx_sales_customer", columnList = "customer_id, created_at"),
        @Index(name = "idx_sales_created_by", columnList = "created_by"),
        @Index(name = "idx_sales_payment_method", columnList = "payment_method"),
        @Index(name = "idx_sales_status", columnList = "status")
})
public class Sale extends ImmutableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    /** User that performed the sale. */
    @Column(name = "created_by")
    private Long createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;
    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_provider", length = 30)
    private TransferProvider transferProvider;
    @Column(name = "transfer_account_name", length = 120)
    private String transferAccountName;
    @Column(name = "transfer_account_identifier", length = 120)
    private String transferAccountIdentifier;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "amount_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "amount_due", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountDue = BigDecimal.ZERO;

    /** Cost of goods sold at the moment of sale (weighted average). */
    @Column(name = "cost_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal costTotal = BigDecimal.ZERO;

    /** total - costTotal */
    @Column(name = "profit_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal profitTotal = BigDecimal.ZERO;

    @Column(name = "refunded_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal refundedTotal = BigDecimal.ZERO;

    @Column(name = "refunded_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal refundedCost = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SaleStatus status = SaleStatus.COMPLETED;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = false)
    @BatchSize(size = 50)
    private List<SaleItem> items = new ArrayList<>();

    protected Sale() {
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    /**
     * Records where a {@code BANK_TRANSFER} payment went. All three fields move together:
     * either a sale has a complete destination or none at all, which is what the
     * {@code ck_sales_transfer_complete} constraint enforces in the database.
     */
    public void setTransferDetails(TransferProvider provider,
                                   String accountName,
                                   String accountIdentifier) {
        this.transferProvider = provider;
        this.transferAccountName = accountName;
        this.transferAccountIdentifier = accountIdentifier;
    }

    public TransferProvider getTransferProvider() {
        return transferProvider;
    }

    public String getTransferAccountName() {
        return transferAccountName;
    }

    public String getTransferAccountIdentifier() {
        return transferAccountIdentifier;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(BigDecimal discount) {
        this.discount = discount;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(BigDecimal amountPaid) {
        this.amountPaid = amountPaid;
    }

    public BigDecimal getAmountDue() {
        return amountDue;
    }

    public void setAmountDue(BigDecimal amountDue) {
        this.amountDue = amountDue;
    }

    public BigDecimal getCostTotal() {
        return costTotal;
    }

    public void setCostTotal(BigDecimal costTotal) {
        this.costTotal = costTotal;
    }

    public BigDecimal getProfitTotal() {
        return profitTotal;
    }

    public void setProfitTotal(BigDecimal profitTotal) {
        this.profitTotal = profitTotal;
    }

    public BigDecimal getRefundedTotal() {
        return refundedTotal;
    }

    public void setRefundedTotal(BigDecimal refundedTotal) {
        this.refundedTotal = refundedTotal;
    }

    public BigDecimal getRefundedCost() {
        return refundedCost;
    }

    public void setRefundedCost(BigDecimal refundedCost) {
        this.refundedCost = refundedCost;
    }

    public SaleStatus getStatus() {
        return status;
    }

    public void setStatus(SaleStatus status) {
        this.status = status;
    }

    public List<SaleItem> getItems() {
        return items;
    }

    public void addItem(SaleItem item) {
        items.add(item);
        item.setSale(this);
    }
}
