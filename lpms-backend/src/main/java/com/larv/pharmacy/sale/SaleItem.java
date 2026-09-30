package com.larv.pharmacy.sale;

import com.larv.pharmacy.common.domain.ImmutableEntity;
import com.larv.pharmacy.drug.Drug;
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

/**
 * One line of a sale, carrying the historical snapshot taken at sale time:
 * drug name, selling price and the weighted-average cost basis.
 * Later purchases never change these values.
 */
@Entity
@Table(name = "sale_items", indexes = {
        @Index(name = "idx_sale_items_sale", columnList = "sale_id"),
        @Index(name = "idx_sale_items_drug", columnList = "drug_id")
})
public class SaleItem extends ImmutableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drug_id", nullable = false)
    private Drug drug;

    /** Historical drug name (survives drug renames/deletion). */
    @Column(name = "drug_name", nullable = false, length = 150)
    private String drugName;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_selling_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitSellingPrice;

    /** Weighted-average cost at the moment of sale. */
    @Column(name = "unit_cost_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCostPrice;

    /** This line's share of the sale-level discount. */
    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /** Gross line amount: quantity x unitSellingPrice. */
    @Column(name = "line_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineTotal;

    /** Net of discount: lineTotal - discountAmount. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal revenue;

    @Column(name = "cost_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal costTotal;

    /** revenue - costTotal */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal profit;

    @Column(name = "refunded_quantity", nullable = false)
    private int refundedQuantity;

    @Column(name = "refunded_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "refunded_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal refundedCost = BigDecimal.ZERO;

    protected SaleItem() {
    }

    public SaleItem(Sale sale, Drug drug, String drugName, int quantity,
                    BigDecimal unitSellingPrice, BigDecimal unitCostPrice,
                    BigDecimal discountAmount, BigDecimal lineTotal,
                    BigDecimal revenue, BigDecimal costTotal, BigDecimal profit) {
        this.sale = sale;
        this.drug = drug;
        this.drugName = drugName;
        this.quantity = quantity;
        this.unitSellingPrice = unitSellingPrice;
        this.unitCostPrice = unitCostPrice;
        this.discountAmount = discountAmount;
        this.lineTotal = lineTotal;
        this.revenue = revenue;
        this.costTotal = costTotal;
        this.profit = profit;
    }

    public Long getId() {
        return id;
    }

    public Sale getSale() {
        return sale;
    }

    public void setSale(Sale sale) {
        this.sale = sale;
    }

    public Drug getDrug() {
        return drug;
    }

    public String getDrugName() {
        return drugName;
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

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public BigDecimal getRevenue() {
        return revenue;
    }

    public BigDecimal getCostTotal() {
        return costTotal;
    }

    public BigDecimal getProfit() {
        return profit;
    }

    public int getRefundedQuantity() {
        return refundedQuantity;
    }

    public void setRefundedQuantity(int refundedQuantity) {
        this.refundedQuantity = refundedQuantity;
    }

    public BigDecimal getRefundedAmount() {
        return refundedAmount;
    }

    public void setRefundedAmount(BigDecimal refundedAmount) {
        this.refundedAmount = refundedAmount;
    }

    public BigDecimal getRefundedCost() {
        return refundedCost;
    }

    public void setRefundedCost(BigDecimal refundedCost) {
        this.refundedCost = refundedCost;
    }

    public int refundableQuantity() {
        return quantity - refundedQuantity;
    }
}
