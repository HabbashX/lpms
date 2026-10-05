package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;

/**
 * {@code DrugValuationResponse} — {@code GET /inventory/drugs/{drugId}/valuation}.
 *
 * <p>{@code totalInventoryCost = Σ(remaining × unitPurchasePrice)} and
 * {@code weightedAverageCost = totalInventoryCost / totalQuantity}. The weighting is
 * quantity-based, not a plain mean: 300 units @ 300 + 300 units @ 230 → 265.</p>
 */
public final class DrugValuationResponse {

    @SerializedName("drugId")
    private final Long drugId;

    @SerializedName("drugName")
    private final String drugName;

    @SerializedName("totalQuantity")
    private final Long totalQuantity;

    @SerializedName("totalInventoryCost")
    private final BigDecimal totalInventoryCost;

    @SerializedName("weightedAverageCost")
    private final BigDecimal weightedAverageCost;

    public DrugValuationResponse(@Nullable Long drugId,
                                 @Nullable String drugName,
                                 @Nullable Long totalQuantity,
                                 @Nullable BigDecimal totalInventoryCost,
                                 @Nullable BigDecimal weightedAverageCost) {
        this.drugId = drugId;
        this.drugName = drugName;
        this.totalQuantity = totalQuantity;
        this.totalInventoryCost = totalInventoryCost;
        this.weightedAverageCost = weightedAverageCost;
    }

    @Nullable
    public Long getDrugId() {
        return drugId;
    }

    @Nullable
    public String getDrugName() {
        return drugName;
    }

    public long getTotalQuantity() {
        return totalQuantity == null ? 0L : totalQuantity;
    }

    @Nullable
    public BigDecimal getTotalInventoryCost() {
        return totalInventoryCost;
    }

    @Nullable
    public BigDecimal getWeightedAverageCost() {
        return weightedAverageCost;
    }

    @NonNull
    @Override
    public String toString() {
        return (drugName == null ? "" : drugName) + " qty=" + getTotalQuantity();
    }
}