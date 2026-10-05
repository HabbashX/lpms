package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * {@code POST /customers} body.
 *
 * <p>Validation: name required ≤100 chars, phone ≤30, address ≤255, notes ≤500.</p>
 *
 * <p>Available to every role — EMPLOYEE may create customers.</p>
 */
public final class CreateCustomerRequest {

    @SerializedName("name")
    private final String name;

    @SerializedName("phone")
    private final String phone;

    @SerializedName("address")
    private final String address;

    @SerializedName("notes")
    private final String notes;

    public CreateCustomerRequest(@Nullable String name,
                                 @Nullable String phone,
                                 @Nullable String address,
                                 @Nullable String notes) {
        this.name = name;
        this.phone = phone;
        this.address = address;
        this.notes = notes;
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
}