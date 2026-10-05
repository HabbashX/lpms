package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * {@code PUT /customers/{id}} body. Available to every role.
 *
 * <p>Passing {@code active=false} is the supported way to deactivate a customer —
 * the ledger and all historical transactions are preserved. A deactivated customer
 * cannot be used for a new sale (409 {@code CUSTOMER_INACTIVE}).</p>
 */
public final class UpdateCustomerRequest {

    @SerializedName("name")
    private final String name;

    @SerializedName("phone")
    private final String phone;

    @SerializedName("address")
    private final String address;

    @SerializedName("notes")
    private final String notes;

    @SerializedName("active")
    private final Boolean active;

    public UpdateCustomerRequest(@Nullable String name,
                                 @Nullable String phone,
                                 @Nullable String address,
                                 @Nullable String notes,
                                 @Nullable Boolean active) {
        this.name = name;
        this.phone = phone;
        this.address = address;
        this.notes = notes;
        this.active = active;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @Nullable
    public String getPhone() {
        return phone;
    }

    @Nullable
    public String getAddress() {
        return address;
    }

    @Nullable
    public String getNotes() {
        return notes;
    }

    @Nullable
    public Boolean getActive() {
        return active;
    }

    /** Deactivate: same payload with {@code active=false}. */
    @NonNull
    public static UpdateCustomerRequest deactivate(@NonNull CustomerResponse customer) {
        return new UpdateCustomerRequest(customer.getName(), customer.getPhone(),
                customer.getAddress(), customer.getNotes(), Boolean.FALSE);
    }
}