package com.lpms.data.dto;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * {@code POST /auth/logout} body (optional). Including the refresh token revokes it
 * too, instead of leaving it usable until natural expiry.
 *
 * <p>Local storage is cleared regardless of the server's answer, so logout always
 * succeeds from the user's point of view.</p>
 */
public final class LogoutRequest {

    @SerializedName("refreshToken")
    private final String refreshToken;

    public LogoutRequest(@Nullable String refreshToken) {
        this.refreshToken = refreshToken;
    }

    @Nullable
    public String getRefreshToken() {
        return refreshToken;
    }
}