package com.lpms.data.dto;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.time.Instant;

/**
 * {@code GET /auth/me}, and the user shape of {@code GET /users} and
 * {@code GET /users/{id}}.
 *
 * <p>{@link #isMustChangePassword()} drives the forced Change Password screen: while
 * it is true the backend answers every endpoint except {@code /auth/change-password},
 * {@code /auth/logout} and {@code /auth/me} with 403
 * {@code PASSWORD_CHANGE_REQUIRED}.</p>
 */
public final class UserResponse {

    @SerializedName("id")
    private final Long id;

    @SerializedName("username")
    private final String username;

    @SerializedName("role")
    private final String role;

    @SerializedName("enabled")
    private final Boolean enabled;

    @SerializedName("mustChangePassword")
    private final Boolean mustChangePassword;

    @SerializedName("lastLoginAt")
    private final Instant lastLoginAt;

    @SerializedName("createdAt")
    private final Instant createdAt;

    @SerializedName("updatedAt")
    private final Instant updatedAt;

    public UserResponse(@Nullable Long id,
                        @Nullable String username,
                        @Nullable String role,
                        @Nullable Boolean enabled,
                        @Nullable Boolean mustChangePassword,
                        @Nullable Instant lastLoginAt,
                        @Nullable Instant createdAt,
                        @Nullable Instant updatedAt) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.enabled = enabled;
        this.mustChangePassword = mustChangePassword;
        this.lastLoginAt = lastLoginAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @Nullable
    public Long getId() {
        return id;
    }

    @Nullable
    public String getUsername() {
        return username;
    }

    @Nullable
    public String getRole() {
        return role;
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public boolean isMustChangePassword() {
        return Boolean.TRUE.equals(mustChangePassword);
    }

    @Nullable
    public Instant getLastLoginAt() {
        return lastLoginAt;
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