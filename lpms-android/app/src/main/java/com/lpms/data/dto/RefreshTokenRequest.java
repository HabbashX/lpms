package com.lpms.data.dto;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

/**
 * {@code POST /auth/refresh} body. Public call: never send an Authorization header.
 *
 * <p>The refresh token is single-use. Sending an already-consumed token makes the
 * server revoke <b>all</b> sessions of that user, so this body is issued at most
 * once per token.</p>
 */
public final class RefreshTokenRequest {

    @SerializedName("refreshToken")
    private final String refreshToken;

    public RefreshTokenRequest(@NonNull String refreshToken) {
        this.refreshToken = refreshToken;
    }

    @NonNull
    public String getRefreshToken() {
        return refreshToken;
    }
}