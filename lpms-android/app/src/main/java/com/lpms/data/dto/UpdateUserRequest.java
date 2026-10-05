package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * {@code PUT /users/{id}} body. ADMIN only.
 *
 * <p>{@code role} is required; {@code username} is optional and omitted when
 * unchanged.</p>
 *
 * <p>409 {@code SELF_MODIFICATION_FORBIDDEN} when the admin targets themselves, and
 * 409 {@code LAST_ADMIN} when the change would remove the final active admin.</p>
 */
public final class UpdateUserRequest {

    @SerializedName("username")
    private final String username;

    @SerializedName("role")
    private final String role;

    public UpdateUserRequest(@Nullable String username, @NonNull String role) {
        this.username = username;
        this.role = role;
    }

    @Nullable
    public String getUsername() {
        return username;
    }

    @NonNull
    public String getRole() {
        return role;
    }
}