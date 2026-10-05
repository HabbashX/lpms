package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/** Ledger adjustment direction: {@code DEBIT} adds debt, {@code CREDIT} removes it. */
public enum TransactionDirection {

    @SerializedName("DEBIT")
    DEBIT,

    @SerializedName("CREDIT")
    CREDIT;

    @NonNull
    public static TransactionDirection fromNullable(@Nullable String raw) {
        if (raw == null) {
            return DEBIT;
        }
        for (TransactionDirection direction : values()) {
            if (direction.name().equalsIgnoreCase(raw.trim())) {
                return direction;
            }
        }
        return DEBIT;
    }

    @NonNull
    public String wireValue() {
        return name();
    }
}