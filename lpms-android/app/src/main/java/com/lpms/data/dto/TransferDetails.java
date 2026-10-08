package com.lpms.data.dto;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

/**
 * The {@code transfer} object of {@link CreateSaleRequest}: where a bank transfer payment
 * is sent. Sent only when {@code paymentMethod} is {@link PaymentMethod#BANK_TRANSFER} -
 * the server rejects it on any other payment method.
 */
public final class TransferDetails {

    @SerializedName("provider")
    private final String provider;

    @SerializedName("accountName")
    private final String accountName;

    @SerializedName("accountIdentifier")
    private final String accountIdentifier;

    public TransferDetails(@NonNull TransferProvider provider,
                           @NonNull String accountName,
                           @NonNull String accountIdentifier) {
        this.provider = provider.wireName();
        this.accountName = accountName.trim();
        this.accountIdentifier = accountIdentifier.trim();
    }

    @NonNull
    public String getProvider() {
        return provider;
    }

    @NonNull
    public String getAccountName() {
        return accountName;
    }

    @NonNull
    public String getAccountIdentifier() {
        return accountIdentifier;
    }

    /** True when both free-text fields actually hold something. */
    public boolean isComplete() {
        return provider != null && !provider.isEmpty()
                && accountName != null && !accountName.trim().isEmpty()
                && accountIdentifier != null && !accountIdentifier.trim().isEmpty();
    }
}
