package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * {@code ExpiringBatchResponse} — {@code GET /inventory/expiring}.
 *
 * <p>{@code days} is 7 / 30 / 90 (the server default comes from the
 * {@code inventory.expiring_soon_days} setting). {@code expired=true} returns only
 * already-expired batches. {@code daysUntilExpiration} is negative once expired.</p>
 */
public final class ExpiringBatchResponse {

    @SerializedName("batchId")
    private final Long batchId;

    @SerializedName("drugId")
    private final Long drugId;

    @SerializedName("drugName")
    private final String drugName;

    @SerializedName("batchNumber")
    private final String batchNumber;

    @SerializedName("expirationDate")
    private final LocalDate expirationDate;

    @SerializedName("daysUntilExpiration")
    private final Long daysUntilExpiration;

    @SerializedName("expired")
    private final Boolean expired;

    @SerializedName("remainingQuantity")
    private final Integer remainingQuantity;

    @SerializedName("unitPurchasePrice")
    private final BigDecimal unitPurchasePrice;

    @SerializedName("supplier")
    private final String supplier;

    public ExpiringBatchResponse(@Nullable Long batchId,
                                 @Nullable Long drugId,
                                 @Nullable String drugName,
                                 @Nullable String batchNumber,
                                 @Nullable LocalDate expirationDate,
                                 @Nullable Long daysUntilExpiration,
                                 @Nullable Boolean expired,
                                 @Nullable Integer remainingQuantity,
                                 @Nullable BigDecimal unitPurchasePrice,
                                 @Nullable String supplier) {
        this.batchId = batchId;
        this.drugId = drugId;
        this.drugName = drugName;
        this.batchNumber = batchNumber;
        this.expirationDate = expirationDate;
        this.daysUntilExpiration = daysUntilExpiration;
        this.expired = expired;
        this.remainingQuantity = remainingQuantity;
        this.unitPurchasePrice = unitPurchasePrice;
        this.supplier = supplier;
    }

    @Nullable
    public Long getBatchId() {
        return batchId;
    }

    @Nullable
    public Long getDrugId() {
        return drugId;
    }

    @Nullable
    public String getDrugName() {
        return drugName;
    }

    @Nullable
    public String getBatchNumber() {
        return batchNumber;
    }

    @Nullable
    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public long getDaysUntilExpiration() {
        return daysUntilExpiration == null ? 0L : daysUntilExpiration;
    }

    public boolean isExpired() {
        return Boolean.TRUE.equals(expired) || getDaysUntilExpiration() < 0;
    }

    public int getRemainingQuantity() {
        return remainingQuantity == null ? 0 : remainingQuantity;
    }

    @Nullable
    public BigDecimal getUnitPurchasePrice() {
        return unitPurchasePrice;
    }

    @Nullable
    public String getSupplier() {
        return supplier;
    }

    /** expired → red, ≤7d → red, ≤30d → orange. */
    @NonNull
    public StockBatchResponse.ExpirationSeverity severity() {
        if (isExpired()) {
            return StockBatchResponse.ExpirationSeverity.EXPIRED;
        }
        long days = getDaysUntilExpiration();
        if (days <= 7) {
            return StockBatchResponse.ExpirationSeverity.CRITICAL;
        }
        if (days <= 30) {
            return StockBatchResponse.ExpirationSeverity.WARNING;
        }
        return StockBatchResponse.ExpirationSeverity.NONE;
    }
}