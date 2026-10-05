package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** {@code StockBatchResponse} — returned by {@code POST /inventory/purchases}, {@code GET /inventory/batches} and {@code GET /inventory/drugs/{drugId}}. */
public final class StockBatchResponse {

    @SerializedName("id")
    private final Long id;

    @SerializedName("drugId")
    private final Long drugId;

    @SerializedName("drugName")
    private final String drugName;

    @SerializedName("quantityReceived")
    private final Integer quantityReceived;

    @SerializedName("remainingQuantity")
    private final Integer remainingQuantity;

    @SerializedName("unitPurchasePrice")
    private final BigDecimal unitPurchasePrice;

    @SerializedName("supplier")
    private final String supplier;

    @SerializedName("batchNumber")
    private final String batchNumber;

    @SerializedName("expirationDate")
    private final LocalDate expirationDate;

    @SerializedName("receivedAt")
    private final Instant receivedAt;

    @SerializedName("createdAt")
    private final Instant createdAt;

    public StockBatchResponse(@Nullable Long id,
                              @Nullable Long drugId,
                              @Nullable String drugName,
                              @Nullable Integer quantityReceived,
                              @Nullable Integer remainingQuantity,
                              @Nullable BigDecimal unitPurchasePrice,
                              @Nullable String supplier,
                              @Nullable String batchNumber,
                              @Nullable LocalDate expirationDate,
                              @Nullable Instant receivedAt,
                              @Nullable Instant createdAt) {
        this.id = id;
        this.drugId = drugId;
        this.drugName = drugName;
        this.quantityReceived = quantityReceived;
        this.remainingQuantity = remainingQuantity;
        this.unitPurchasePrice = unitPurchasePrice;
        this.supplier = supplier;
        this.batchNumber = batchNumber;
        this.expirationDate = expirationDate;
        this.receivedAt = receivedAt;
        this.createdAt = createdAt;
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

    public int getQuantityReceived() {
        return quantityReceived == null ? 0 : quantityReceived;
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

    @Nullable
    public String getBatchNumber() {
        return batchNumber;
    }

    @Nullable
    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    @Nullable
    public Instant getReceivedAt() {
        return receivedAt;
    }

    @Nullable
    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isFullyConsumed() {
        return getRemainingQuantity() <= 0;
    }

    /**
     * Days until expiration against the device's current date.
     *
     * @return negative when already expired, or {@link Long#MAX_VALUE} when the batch
     * has no expiration date (treated as "never expires").
     */
    public long daysUntilExpiration(@NonNull java.time.Clock clock) {
        LocalDate expiry = expirationDate;
        if (expiry == null) {
            return Long.MAX_VALUE;
        }
        LocalDate today = java.time.LocalDate.now(clock);
        return ChronoUnit.DAYS.between(today, expiry);
    }

    /** Drives the inventory colour coding: expired red, ≤7d red, ≤30d orange. */
    @NonNull
    public ExpirationSeverity expirationSeverity(@NonNull java.time.Clock clock) {
        long days = daysUntilExpiration(clock);
        if (days == Long.MAX_VALUE) {
            return ExpirationSeverity.NONE;
        }
        if (days < 0) {
            return ExpirationSeverity.EXPIRED;
        }
        if (days <= 7) {
            return ExpirationSeverity.CRITICAL;
        }
        if (days <= 30) {
            return ExpirationSeverity.WARNING;
        }
        return ExpirationSeverity.NONE;
    }

    /** Local severity buckets for batch expiry colouring. */
    public enum ExpirationSeverity {
        NONE,
        WARNING,
        CRITICAL,
        EXPIRED
    }
}