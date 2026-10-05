package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;

/**
 * One ledger entry of a customer account.
 *
 * <p>DEBIT increases the debt, CREDIT decreases it; {@link #getBalance()} is the
 * running balance <em>after</em> this entry.</p>
 */
public final class TransactionResponse {

    @SerializedName("id")
    private final Long id;

    @SerializedName("date")
    private final java.time.Instant date;

    @SerializedName("type")
    private final String type;

    @SerializedName("description")
    private final String description;

    @SerializedName("debit")
    private final BigDecimal debit;

    @SerializedName("credit")
    private final BigDecimal credit;

    @SerializedName("balance")
    private final BigDecimal balance;

    /** e.g. {@code "SALE #12"}. */
    @SerializedName("reference")
    private final String reference;

    @SerializedName("createdBy")
    private final String createdBy;

    public TransactionResponse(@Nullable Long id,
                                @Nullable java.time.Instant date,
                                @Nullable String type,
                                @Nullable String description,
                                @Nullable BigDecimal debit,
                                @Nullable BigDecimal credit,
                                @Nullable BigDecimal balance,
                                @Nullable String reference,
                                @Nullable String createdBy) {
        this.id = id;
        this.date = date;
        this.type = type;
        this.description = description;
        this.debit = debit;
        this.credit = credit;
        this.balance = balance;
        this.reference = reference;
        this.createdBy = createdBy;
    }

    @Nullable
    public Long getId() {
        return id;
    }

    @Nullable
    public java.time.Instant getDate() {
        return date;
    }

    @NonNull
    public TransactionType transactionType() {
        return TransactionType.fromNullable(type);
    }

    @Nullable
    public String getType() {
        return type;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    /** Amount added to the debt. */
    @Nullable
    public BigDecimal getDebit() {
        return debit;
    }

    /** Amount removed from the debt. */
    @Nullable
    public BigDecimal getCredit() {
        return credit;
    }

    @Nullable
    public BigDecimal getBalance() {
        return balance;
    }

    @Nullable
    public String getReference() {
        return reference;
    }

    @Nullable
    public String getCreatedBy() {
        return createdBy;
    }

    /** Signed effect on the debt: positive for a debit, negative for a credit. */
    @NonNull
    public BigDecimal signedAmount() {
        BigDecimal d = debit == null ? BigDecimal.ZERO : debit;
        BigDecimal c = credit == null ? BigDecimal.ZERO : credit;
        return d.subtract(c);
    }
}