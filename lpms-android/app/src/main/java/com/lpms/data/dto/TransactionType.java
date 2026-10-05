package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * Ledger entry type: {@code SALE}, {@code PAYMENT}, {@code ADJUSTMENT}, {@code REFUND}.
 * Unknown values from a newer server degrade to {@link #UNKNOWN} rather than throwing.
 */
public enum TransactionType {

    @SerializedName("SALE")
    SALE,

    @SerializedName("PAYMENT")
    PAYMENT,

    @SerializedName("ADJUSTMENT")
    ADJUSTMENT,

    @SerializedName("REFUND")
    REFUND,

    /** Not part of the contract; used only as a defensive fallback. */
    UNKNOWN;

    @NonNull
    public static TransactionType fromNullable(@Nullable String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        for (TransactionType type : values()) {
            if (type != UNKNOWN && type.name().equalsIgnoreCase(raw.trim())) {
                return type;
            }
        }
        return UNKNOWN;
    }

    @NonNull
    public String wireValue() {
        return this == UNKNOWN ? "ADJUSTMENT" : name();
    }
}