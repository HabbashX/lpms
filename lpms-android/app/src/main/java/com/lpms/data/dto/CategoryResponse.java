package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * {@code GET /categories} returns a plain, <b>non-paginated</b> list of
 * {@code {id, name}} sorted by name. Cached once and reused by the drug form
 * dropdown and the drug-list filter chip; refreshed after any category write.
 */
public final class CategoryResponse {

    @SerializedName("id")
    private final Long id;

    @SerializedName("name")
    private final String name;

    public CategoryResponse(@Nullable Long id, @Nullable String name) {
        this.id = id;
        this.name = name;
    }

    @Nullable
    public Long getId() {
        return id;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @NonNull
    @Override
    public String toString() {
        return name == null ? "" : name;
    }
}