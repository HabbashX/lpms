package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * {@code POST /customers/{id}/payments} body → 201 {@link PaymentResponse}.
 *
 * <p>Available to every role. Amount must be &gt; 0; paying more than the current
 * debt is 409 {@code PAYMENT_EXCEEDS_DEBT} unless the admin-enabled setting
 * {@code customer.allow_negative_balance} is true.</p>
 */
public final class CreatePaymentRequest {

    public static final int NOTES_MAX_LENGTH = 255;

    @SerializedName("amount")
    private final java.math.BigDecimal amount;

    @SerializedName("paymentMethod")
    private final String paymentMethod;

    @SerializedName("notes")
    private final String notes;

    public CreatePaymentRequest(@NonNull java.math.BigDecimal amount,
                                @NonNull PaymentMethod paymentMethod,
                                @Nullable String notes) {
        this.amount = amount;
        this.paymentMethod = paymentMethod.wireValue();
        this.notes = trimNotes(notes);
    }

    @NonNull
    public java.math.BigDecimal getAmount() {
        return amount;
    }

    @NonNull
    public String getPaymentMethod() {
        return paymentMethod;
    }

    @Nullable
    public String getNotes() {
        return notes;
    }

    @Nullable
    private static String trimNotes(@Nullable String notes) {
        if (notes == null) {
            return null;
        }
        String trimmed = notes.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > NOTES_MAX_LENGTH
                ? trimmed.substring(0, NOTES_MAX_LENGTH)
                : trimmed;
    }
}