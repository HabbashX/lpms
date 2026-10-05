package com.lpms.data.dto;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Per-batch row inside {@link DrugPricingResponse}. */
public final class PricingBatchResponse {

    @SerializedName("batchId")
    private final Long batchId;

    @SerializedName("batchNumber")
    private final String batchNumber;

    @SerializedName("remainingQuantity")
    private final Integer remainingQuantity;

    @SerializedName("unitPurchasePrice")
    private final BigDecimal unitPurchasePrice;

    @SerializedName("expirationDate")
    private final LocalDate expirationDate;

    public PricingBatchResponse(@Nullable Long batchId,
                                @Nullable String batchNumber,
                                @Nullable Integer remainingQuantity,
                                @Nullable BigDecimal unitPurchasePrice,
                                @Nullable LocalDate expirationDate) {
        this.batchId = batchId;
        this.batchNumber = batchNumber;
        this.remainingQuantity = remainingQuantity;
        this.unitPurchasePrice = unitPurchasePrice;
        this.expirationDate = expirationDate;
    }

    @Nullable
    public Long getBatchId() {
        return batchId;
    }

    @Nullable
    public String getBatchNumber() {
        return batchNumber;
    }

    public int getRemainingQuantity() {
        return remainingQuantity == null ? 0 : remainingQuantity;
    }

    @Nullable
    public BigDecimal getUnitPurchasePrice() {
        return unitPurchasePrice;
    }

    @Nullable
    public LocalDate getExpirationDate() {
        return expirationDate;
    }
}