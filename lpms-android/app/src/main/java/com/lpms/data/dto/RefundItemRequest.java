package com.lpms.data.dto;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;

/** One line of {@code CreateRefundRequest}: {@code {saleItemId, quantity}} with quantity &gt; 0. */
public final class RefundItemRequest {

    @SerializedName("saleItemId")
    private final Long saleItemId;

    @SerializedName("quantity")
    private final Integer quantity;

    public RefundItemRequest(@NonNull Long saleItemId, @NonNull Integer quantity) {
        this.saleItemId = saleItemId;
        this.quantity = quantity;
    }

    @NonNull
    public Long getSaleItemId() {
        return saleItemId;
    }

    @NonNull
    public Integer getQuantity() {
        return quantity;
    }
}