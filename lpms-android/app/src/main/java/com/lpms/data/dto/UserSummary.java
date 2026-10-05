package com.lpms.data.dto;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/** Compact user embedded in login/refresh responses: {@code {id, username, role}}. */
public final class UserSummary {

    @SerializedName("id")
    private final Long id;

    @SerializedName("username")
    private final String username;

    @SerializedName("role")
    private final String role;

    public UserSummary(@Nullable Long id, @Nullable String username, @Nullable String role) {
        this.id = id;
        this.username = username;
        this.role = role;
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
}