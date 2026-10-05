package com.lpms.data.dto;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * {@code GET /} — public health probe, used for the connectivity check on the
 * login screen. Expected body: {@code {"name":"lpms-backend","status":"UP"}}.
 */
public final class HealthResponse {

    @SerializedName("name")
    private final String name;

    @SerializedName("status")
    private final String status;

    public HealthResponse(@Nullable String name, @Nullable String status) {
        this.name = name;
        this.status = status;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    public boolean isUp() {
        return status != null && status.equalsIgnoreCase("UP");
    }
}