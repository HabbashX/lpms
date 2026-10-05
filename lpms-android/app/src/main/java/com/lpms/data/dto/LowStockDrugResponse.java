package com.lpms.data.dto;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/** {@code LowStockDrugResponse} — {@code GET /inventory/low-stock}. */
public final class LowStockDrugResponse {

    @SerializedName("drugId")
    private final Long drugId;

    @SerializedName("name")
    private final String name;

    @SerializedName("currentQuantity")
    private final Integer currentQuantity;

    @SerializedName("minimumStockLevel")
    private final Integer minimumStockLevel;

    @SerializedName("unit")
    private final String unit;

    public LowStockDrugResponse(@Nullable Long drugId,
                                @Nullable String name,
                                @Nullable Integer currentQuantity,
                                @Nullable Integer minimumStockLevel,
                                @Nullable String unit) {
        this.drugId = drugId;
        this.name = name;
        this.currentQuantity = currentQuantity;
        this.minimumStockLevel = minimumStockLevel;
        this.unit = unit;
    }

    @Nullable
    public Long getDrugId() {
        return drugId;
    }

    @Nullable
    public String getName() {
        return name;
    }

    public int getCurrentQuantity() {
        return currentQuantity == null ? 0 : currentQuantity;
    }

    public int getMinimumStockLevel() {
        return minimumStockLevel == null ? 0 : minimumStockLevel;
    }

    @Nullable
    public String getUnit() {
        return unit;
    }
}