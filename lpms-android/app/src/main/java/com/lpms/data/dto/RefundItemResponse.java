package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;

/** One refunded line inside {@link RefundResponse}. */
public final class RefundItemResponse {

    @SerializedName("saleItemId")
    private final Long saleItemId;

    @SerializedName("drugName")
    private final String drugName;

    @SerializedName("quantity")
    private final Integer quantity;

    @SerializedName("unitSellingPrice")
    private final BigDecimal unitSellingPrice;

    @SerializedName("unitCostPrice")
    private final BigDecimal unitCostPrice;

    @SerializedName("amount")
    private final BigDecimal amount;

    @SerializedName("cost")
    private final BigDecimal cost;

    @SerializedName("profit")
    private final BigDecimal profit;

    public RefundItemResponse(@Nullable Long saleItemId,
                              @Nullable String drugName,
                              @Nullable Integer quantity,
                              @Nullable BigDecimal unitSellingPrice,
                              @Nullable BigDecimal unitCostPrice,
                              @Nullable BigDecimal amount,
                              @Nullable BigDecimal cost,
                              @Nullable BigDecimal profit) {
        this.saleItemId = saleItemId;
        this.drugName = drugName;
        this.quantity = quantity;
        this.unitSellingPrice = unitSellingPrice;
        this.unitCostPrice = unitCostPrice;
        this.amount = amount;
        this.cost = cost;
        this.profit = profit;
    }

    @Nullable
    public Long getSaleItemId() {
        return saleItemId;
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

    @Nullable
    public BigDecimal getUnitCostPrice() {
        return unitCostPrice;
    }

    /** Refunded amount for this line. */
    @Nullable
    public BigDecimal getAmount() {
        return amount;
    }

    @Nullable
    public BigDecimal getCost() {
        return cost;
    }

    @Nullable
    public BigDecimal getProfit() {
        return profit;
    }

    @NonNull
    public String displayName() {
        return drugName == null ? "" : drugName;
    }
}