package com.lpms.data.dto;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.time.Instant;

/**
 * {@code CustomerResponse} — {@code GET /customers}, {@code GET /customers/{id}},
 * and the {@code customer} member of {@link CustomerAccountResponse}.
 */
public final class CustomerResponse {

    @SerializedName("id")
    private final Long id;

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

    @SerializedName("createdAt")
    private final Instant createdAt;

    @SerializedName("updatedAt")
    private final Instant updatedAt;

    public CustomerResponse(@Nullable Long id,
                            @Nullable String name,
                            @Nullable String phone,
                            @Nullable String address,
                            @Nullable String notes,
                            @Nullable Boolean active,
                            @Nullable Instant createdAt,
                            @Nullable Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.address = address;
        this.notes = notes;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @Nullable
    public Long getId() {
        return id;
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

    public boolean isActive() {
        return !Boolean.FALSE.equals(active);
    }

    @Nullable
    public Instant getCreatedAt() {
        return createdAt;
    }

    @Nullable
    public Instant getUpdatedAt() {
        return updatedAt;
    }
}