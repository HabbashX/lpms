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
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "refund_items", indexes = {
        @Index(name = "idx_refund_items_refund", columnList = "refund_id"),
        @Index(name = "idx_refund_items_sale_item", columnList = "sale_item_id")
})
public class RefundItem extends ImmutableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refund_id", nullable = false)
    private Refund refund;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_item_id", nullable = false)
    private SaleItem saleItem;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_selling_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitSellingPrice;

    @Column(name = "unit_cost_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCostPrice;

    /** Reversed net revenue (after discount allocation). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Reversed cost of goods sold. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal cost;

    /** amount - cost */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal profit;

    protected RefundItem() {
    }

    public RefundItem(SaleItem saleItem, int quantity, BigDecimal unitSellingPrice,
                      BigDecimal unitCostPrice, BigDecimal amount, BigDecimal cost, BigDecimal profit) {
        this.saleItem = saleItem;
        this.quantity = quantity;
        this.unitSellingPrice = unitSellingPrice;
        this.unitCostPrice = unitCostPrice;
        this.amount = amount;
        this.cost = cost;
        this.profit = profit;
    }

    public Long getId() {
        return id;
    }

    public Refund getRefund() {
        return refund;
    }

    public void setRefund(Refund refund) {
        this.refund = refund;
    }

    public SaleItem getSaleItem() {
        return saleItem;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitSellingPrice() {
        return unitSellingPrice;
    }

    public BigDecimal getUnitCostPrice() {
        return unitCostPrice;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public BigDecimal getProfit() {
        return profit;
    }
}
