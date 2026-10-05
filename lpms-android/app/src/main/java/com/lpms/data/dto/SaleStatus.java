package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/** {@code SaleStatus} enum: {@code COMPLETED}, {@code PARTIALLY_REFUNDED}, {@code REFUNDED}. */
public enum SaleStatus {

    @SerializedName("COMPLETED")
    COMPLETED,

    @SerializedName("PARTIALLY_REFUNDED")
    PARTIALLY_REFUNDED,

    @SerializedName("REFUNDED")
    REFUNDED;

    @NonNull
    public static SaleStatus fromNullable(@Nullable String raw) {
        if (raw == null) {
            return COMPLETED;
        }
        for (SaleStatus status : values()) {
            if (status.name().equalsIgnoreCase(raw.trim())) {
                return status;
            }
        }
        return COMPLETED;
    }

    @NonNull
    public String wireValue() {
        return name();
    }

    /** No further refund is possible. */
    public boolean isFullyRefunded() {
        return this == REFUNDED;
    }

    public boolean isRefundable() {
        return this != REFUNDED;
    }
}