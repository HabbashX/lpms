package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;
import java.time.Instant;

/** {@code POST /customers/{id}/payments} → 201 {@code PaymentResponse}. */
public final class PaymentResponse {

    @SerializedName("id")
    private final Long id;

    @SerializedName("customerId")
    private final Long customerId;

    @SerializedName("amount")
    private final BigDecimal amount;

    @SerializedName("paymentMethod")
    private final String paymentMethod;

    @SerializedName("notes")
    private final String notes;

    /** Debt remaining after this payment. */
    @SerializedName("balanceAfter")
    private final BigDecimal balanceAfter;

    @SerializedName("createdAt")
    private final Instant createdAt;

    public PaymentResponse(@Nullable Long id,
                           @Nullable Long customerId,
                           @Nullable BigDecimal amount,
                           @Nullable String paymentMethod,
                           @Nullable String notes,
                           @Nullable BigDecimal balanceAfter,
                           @Nullable Instant createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.notes = notes;
        this.balanceAfter = balanceAfter;
        this.createdAt = createdAt;
    }

    @Nullable
    public Long getId() {
        return id;
    }

    @Nullable
    public Long getCustomerId() {
        return customerId;
    }

    @Nullable
    public BigDecimal getAmount() {
        return amount;
    }

    @NonNull
    public PaymentMethod paymentMethod() {
        return PaymentMethod.fromNullable(paymentMethod);
    }

    @Nullable
    public String getPaymentMethod() {
        return paymentMethod;
    }

    @Nullable
    public String getNotes() {
        return notes;
    }

    @Nullable
    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    @Nullable
    public Instant getCreatedAt() {
        return createdAt;
    }
}