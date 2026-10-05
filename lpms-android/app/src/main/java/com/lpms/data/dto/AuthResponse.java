package com.lpms.data.dto;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * {@code POST /auth/login} and {@code POST /auth/refresh} both return this shape.
 *
 * <p>A refresh returns a <b>brand new</b> access token AND a brand new refresh
 * token: refresh tokens are single-use and rotated, so the previous refresh token is
 * dead the moment this response arrives.</p>
 */
public final class AuthResponse {

    @SerializedName("accessToken")
    private final String accessToken;

    @SerializedName("tokenType")
    private final String tokenType;

    @SerializedName("expiresIn")
    private final long expiresIn;

    @SerializedName("refreshToken")
    private final String refreshToken;

    @SerializedName("refreshExpiresIn")
    private final long refreshExpiresIn;

    @SerializedName("user")
    private final UserSummary user;

    public AuthResponse(@Nullable String accessToken,
                        @Nullable String tokenType,
                        long expiresIn,
                        @Nullable String refreshToken,
                        long refreshExpiresIn,
                        @Nullable UserSummary user) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
        this.refreshToken = refreshToken;
        this.refreshExpiresIn = refreshExpiresIn;
        this.user = user;
    }

    @Nullable
    public String getAccessToken() {
        return accessToken;
    }

    /** Always {@code "Bearer"} in the contract; the client hard-codes the prefix. */
    @Nullable
    public String getTokenType() {
        return tokenType;
    }

    /** Access token lifetime in seconds (JWT, default 3600). */
    public long getExpiresIn() {
        return expiresIn;
    }

    @Nullable
    public String getRefreshToken() {
        return refreshToken;
    }

    /** Opaque refresh token lifetime in seconds (default 14 days). */
    public long getRefreshExpiresIn() {
        return refreshExpiresIn;
    }

    @Nullable
    public UserSummary getUser() {
        return user;
    }

    /** True when the payload contains everything needed to establish a session. */
    public boolean isUsable() {
        return accessToken != null && !accessToken.isEmpty()
                && refreshToken != null && !refreshToken.isEmpty();
    }
}