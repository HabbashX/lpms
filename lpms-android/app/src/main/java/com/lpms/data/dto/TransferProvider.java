package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * Where a {@link PaymentMethod#BANK_TRANSFER} payment is sent.
 *
 * <p>Mirrors the server enum. The three options are not variations of one thing, which is
 * why {@link #identifierLabel()} differs: PayPal is identified by an email, Bank of
 * Palestine by an account number, and Jawwal Pay by a phone number.</p>
 */
public enum TransferProvider {

    @SerializedName("PAYPAL")
    PAYPAL("PayPal", "PayPal email", "name@example.com"),

    @SerializedName("BANK_OF_PALESTINE")
    BANK_OF_PALESTINE("Bank of Palestine", "Account number", "e.g. 123456789"),

    @SerializedName("JAWWAL_PAY")
    JAWWAL_PAY("Jawwal Pay", "Phone number", "e.g. 0591234567");

    private final String displayName;
    private final String identifierLabel;
    private final String identifierHint;

    TransferProvider(String displayName, String identifierLabel, String identifierHint) {
        this.displayName = displayName;
        this.identifierLabel = identifierLabel;
        this.identifierHint = identifierHint;
    }

    /** Value the server expects; also the key used for {@code SharedPreferences} style storage. */
    @NonNull
    public String wireName() {
        return name();
    }

    @NonNull
    public String displayName() {
        return displayName;
    }

    /** What the account identifier actually is, so the UI can label the field correctly. */
    @NonNull
    public String identifierLabel() {
        return identifierLabel;
    }

    @NonNull
    public String identifierHint() {
        return identifierHint;
    }

    /** Lenient parse for a server enum name; null when unrecognised. */
    @Nullable
    public static TransferProvider fromWire(@Nullable String value) {
        if (value == null) {
            return null;
        }
        for (TransferProvider provider : values()) {
            if (provider.name().equalsIgnoreCase(value.trim())) {
                return provider;
            }
        }
        return null;
    }
}
