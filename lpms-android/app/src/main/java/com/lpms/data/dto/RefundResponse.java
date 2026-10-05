package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code RefundResponse} — the only refund shape the API returns; there is no
 * refunds list endpoint.
 */
public final class RefundResponse {

    @SerializedName("id")
    private final Long id;

    @SerializedName("saleId")
    private final Long saleId;

    @SerializedName("createdAt")
    private final Instant createdAt;

    @SerializedName("reason")
    private final String reason;

    @SerializedName("totalAmount")
    private final BigDecimal totalAmount;

    @SerializedName("totalCost")
    private final BigDecimal totalCost;

    @SerializedName("totalProfit")
    private final BigDecimal totalProfit;

    @SerializedName("createdBy")
    private final String createdBy;

    @SerializedName("items")
    private final List<RefundItemResponse> items;

    public RefundResponse(@Nullable Long id,
                          @Nullable Long saleId,
                          @Nullable Instant createdAt,
                          @Nullable String reason,
                          @Nullable BigDecimal totalAmount,
                          @Nullable BigDecimal totalCost,
                          @Nullable BigDecimal totalProfit,
                          @Nullable String createdBy,
                          @Nullable List<RefundItemResponse> items) {
        this.id = id;
        this.saleId = saleId;
        this.createdAt = createdAt;
        this.reason = reason;
        this.totalAmount = totalAmount;
        this.totalCost = totalCost;
        this.totalProfit = totalProfit;
        this.createdBy = createdBy;
        this.items = items == null ? new ArrayList<>() : items;
    }

    @Nullable
    public Long getId() {
        return id;
    }

    @Nullable
    public Long getSaleId() {
        return saleId;
    }

    @Nullable
    public Instant getCreatedAt() {
        return createdAt;
    }

    @Nullable
    public String getReason() {
        return reason;
    }

    @Nullable
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    /** ADMIN/PHARMACIST only in the UI (refunds are already restricted to them). */
    @Nullable
    public BigDecimal getTotalCost() {
        return totalCost;
    }

    @Nullable
    public BigDecimal getTotalProfit() {
        return totalProfit;
    }

    @Nullable
    public String getCreatedBy() {
        return createdBy;
    }

    @NonNull
    public List<RefundItemResponse> getItems() {
        return items;
    }
}