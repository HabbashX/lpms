package com.lpms.data.dto;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

/**
 * {@code POST /customers/{id}/adjustments} body (ADMIN/PHARMACIST only).
 *
 * <p>A {@code DEBIT} increases the customer's debt, a {@code CREDIT} decreases it
 * (or creates a credit balance). Amount must be &gt; 0, description required ≤255
 * chars.</p>
 */
public final class CreateAdjustmentRequest {

    public static final int DESCRIPTION_MAX_LENGTH = 255;

    @SerializedName("amount")
    private final java.math.BigDecimal amount;

    @SerializedName("direction")
    private final String direction;

    @SerializedName("description")
    private final String description;

    public CreateAdjustmentRequest(@NonNull java.math.BigDecimal amount,
                                   @NonNull TransactionDirection direction,
                                   @NonNull String description) {
        this.amount = amount;
        this.direction = direction.wireValue();
        this.description = description;
    }

    @NonNull
    public java.math.BigDecimal getAmount() {
        return amount;
    }

    @NonNull
    public String getDirection() {
        return direction;
    }

    @NonNull
    public String getDescription() {
        return description;
    }
}