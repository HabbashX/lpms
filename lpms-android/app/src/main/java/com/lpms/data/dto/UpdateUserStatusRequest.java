package com.lpms.data.dto;

import com.google.gson.annotations.SerializedName;

/**
 * {@code PATCH /users/{id}/status} body.
 *
 * <p>Disabling a user invalidates their access <b>and</b> refresh tokens
 * server-side, so their next call fails with 401 {@code ACCOUNT_DISABLED}.</p>
 */
public final class UpdateUserStatusRequest {

    @SerializedName("enabled")
    private final boolean enabled;

    public UpdateUserStatusRequest(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}