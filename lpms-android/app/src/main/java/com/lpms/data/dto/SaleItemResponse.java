package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;

/**
 * {@code SaleItemResponse}.
 *
 * <p>{@link #getUnitCostPrice()} and {@link #getProfit()} are shown only for
 * ADMIN/PHARMACIST. {@link #getRefundedQuantity()} is what caps the refund stepper:
 * the maximum refundable for this line is {@code quantity − refundedQuantity}.</p>
 */
public final class SaleItemResponse {

    @SerializedName("id")
    private final Long id;

    @SerializedName("drugId")
    private final Long drugId;

    @SerializedName("drugName")
    private final String drugName;

    @SerializedName("quantity")
    private final Integer quantity;

    @SerializedName("unitSellingPrice")
    private final BigDecimal unitSellingPrice;

    @SerializedName("unitCostPrice")
    private final BigDecimal unitCostPrice;

    @SerializedName("discountAmount")
    private final BigDecimal discountAmount;

    @SerializedName("revenue")
    private final BigDecimal revenue;

    @SerializedName("cost")
    private final BigDecimal cost;

    @SerializedName("profit")
    private final BigDecimal profit;

    @SerializedName("refundedQuantity")
    private final Integer refundedQuantity;

    public SaleItemResponse(@Nullable Long id,
                            @Nullable Long drugId,
                            @Nullable String drugName,
                            @Nullable Integer quantity,
                            @Nullable BigDecimal unitSellingPrice,
                            @Nullable BigDecimal unitCostPrice,
                            @Nullable BigDecimal discountAmount,
                            @Nullable BigDecimal revenue,
                            @Nullable BigDecimal cost,
                            @Nullable BigDecimal profit,
                            @Nullable Integer refundedQuantity) {
        this.id = id;
        this.drugId = drugId;
        this.drugName = drugName;
        this.quantity = quantity;
        this.unitSellingPrice = unitSellingPrice;
        this.unitCostPrice = unitCostPrice;
        this.discountAmount = discountAmount;
        this.revenue = revenue;
        this.cost = cost;
        this.profit = profit;
        this.refundedQuantity = refundedQuantity;
    }

    @Nullable
    public Long getId() {
        return id;
    }

    @Nullable
    public Long getDrugId() {
        return drugId;
    }

    @Nullable
    public String getDrugName() {
        return drugName;
    }

    public int getQuantity() {
        return quantity == null ? 0 : quantity;
    }

    @Nullable
    public BigDecimal getUnitSellingPrice() {
        return unitSellingPrice;
    }

    /** ADMIN/PHARMACIST only in the UI. */
    @Nullable
    public BigDecimal getUnitCostPrice() {
        return unitCostPrice;
    }

    @Nullable
    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    @Nullable
    public BigDecimal getRevenue() {
        return revenue;
    }

    /** ADMIN/PHARMACIST only in the UI. */
    @Nullable
    public BigDecimal getCost() {
        return cost;
    }

    /** ADMIN/PHARMACIST only in the UI. */
    @Nullable
    public BigDecimal getProfit() {
        return profit;
    }

    public int getRefundedQuantity() {
        return refundedQuantity == null ? 0 : refundedQuantity;
    }

    /** {@code quantity − refundedQuantity}; 0 means the line is fully refunded. */
    public int getRefundableQuantity() {
        return Math.max(0, getQuantity() - getRefundedQuantity());
    }

    public boolean isFullyRefunded() {
        return getRefundableQuantity() == 0;
    }

    @NonNull
    public String displayName() {
        return drugName == null ? "" : drugName;
    }
}