package com.larv.pharmacy.sale;

import com.larv.pharmacy.common.domain.ImmutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Reversal record of a (partial) refund. The original sale is never deleted
 * or modified beyond its refund-tracking counters, preserving accounting history.
 */
@Entity
@Table(name = "refunds", indexes = {
        @Index(name = "idx_refunds_sale", columnList = "sale_id"),
        @Index(name = "idx_refunds_created_at", columnList = "created_at")
})
public class Refund extends ImmutableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "total_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCost;

    @Column(name = "total_profit", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalProfit;

    @Column(length = 255)
    private String reason;

    @Column(name = "created_by")
    private Long createdBy;

    @OneToMany(mappedBy = "refund", cascade = jakarta.persistence.CascadeType.ALL)
    private List<RefundItem> items = new ArrayList<>();

    protected Refund() {
    }

    public Refund(Sale sale, Long customerId, BigDecimal totalAmount, BigDecimal totalCost,
                  BigDecimal totalProfit, String reason, Long createdBy) {
        this.sale = sale;
        this.customerId = customerId;
        this.totalAmount = totalAmount;
        this.totalCost = totalCost;
        this.totalProfit = totalProfit;
        this.reason = reason;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public Sale getSale() {
        return sale;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public BigDecimal getTotalProfit() {
        return totalProfit;
    }

    public String getReason() {
        return reason;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }

    public void setTotalProfit(BigDecimal totalProfit) {
        this.totalProfit = totalProfit;
    }

    public List<RefundItem> getItems() {
        return items;
    }

    public void addItem(RefundItem item) {
        items.add(item);
        item.setRefund(this);
    }
}
