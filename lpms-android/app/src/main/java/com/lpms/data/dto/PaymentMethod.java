package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;

/**
 * {@code PaymentMethod} enum, exactly as the backend declares it.
 *
 * <p>{@link #CREDIT} is the only method that <b>requires</b> a {@code customerId};
 * a credit sale without a customer is 400 {@code INVALID_SALE}.</p>
 */
public enum PaymentMethod {

    @SerializedName("CASH")
    CASH,

    @SerializedName("CARD")
    CARD,

    @SerializedName("CREDIT")
    CREDIT,

    @SerializedName("BANK_TRANSFER")
    BANK_TRANSFER,

    @SerializedName("INSURANCE")
    INSURANCE;

    @NonNull
    public static PaymentMethod fromNullable(@Nullable String raw) {
        if (raw == null) {
            return CASH;
        }
        for (PaymentMethod method : values()) {
            if (method.name().equalsIgnoreCase(raw.trim())) {
                return method;
            }
        }
        return CASH;
    }

    @NonNull
    public String wireValue() {
        return name();
    }

    /** True when this method can leave an unpaid balance on a customer account. */
    public boolean requiresCustomer() {
        return this == CREDIT;
    }
}