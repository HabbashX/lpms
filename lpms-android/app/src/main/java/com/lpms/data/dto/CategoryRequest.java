package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;

/**
 * {@code POST /categories} and {@code PUT /categories/{id}} body (ADMIN/PHARMACIST).
 *
 * <p>Validation: name required, max 100 chars. Duplicates are rejected
 * case-insensitively with 409 {@code CATEGORY_ALREADY_EXISTS}.</p>
 */
public final class CategoryRequest {

    @SerializedName("name")
    private final String name;

    public CategoryRequest(@NonNull String name) {
        this.name = name;
    }

    @NonNull
    public String getName() {
        return name;
    }
}