package com.lpms.core.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable snapshot of the authenticated session.
 *
 * <p>{@code accessExpiresAt} is stored locally as {@code now + expiresIn} when the
 * token pair is persisted, so it can be checked without decoding the JWT.</p>
 */
public final class Session {

    private final long userId;
    private final String username;
    private final Role role;
    private final boolean mustChangePassword;
    private final String accessToken;
    private final String refreshToken;
    private final Instant accessExpiresAt;
    private final Instant refreshExpiresAt;

    public Session(long userId,
                   @NonNull String username,
                   @NonNull Role role,
                   boolean mustChangePassword,
                   @NonNull String accessToken,
                   @NonNull String refreshToken,
                   @NonNull Instant accessExpiresAt,
                   @NonNull Instant refreshExpiresAt) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.mustChangePassword = mustChangePassword;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.accessExpiresAt = accessExpiresAt;
        this.refreshExpiresAt = refreshExpiresAt;
    }

    public long getUserId() {
        return userId;
    }

    @NonNull
    public String getUsername() {
        return username;
    }

    @NonNull
    public Role getRole() {
        return role;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    @NonNull
    public String getAccessToken() {
        return accessToken;
    }

    @NonNull
    public String getRefreshToken() {
        return refreshToken;
    }

    @NonNull
    public Instant getAccessExpiresAt() {
        return accessExpiresAt;
    }

    @NonNull
    public Instant getRefreshExpiresAt() {
        return refreshExpiresAt;
    }

    public boolean isAccessTokenExpired() {
        return isAccessTokenExpired(java.time.Duration.ofSeconds(30));
    }

    /**
     * Proactive check: the authenticator refreshes slightly early so a request is
     * never sent with a token that will expire in flight.
     */
    public boolean isAccessTokenExpired(@NonNull java.time.Duration leeway) {
        return !Instant.now().plus(leeway).isBefore(accessExpiresAt);
    }

    public boolean isRefreshTokenExpired() {
        return !Instant.now().isBefore(refreshExpiresAt);
    }

    /** Role / mustChangePassword can change on {@code GET /auth/me} without a new token. */
    @NonNull
    public Session withProfile(long newUserId,
                               @NonNull String newUsername,
                               @NonNull Role newRole,
                               boolean newMustChangePassword) {
        return new Session(newUserId, newUsername, newRole, newMustChangePassword,
                accessToken, refreshToken, accessExpiresAt, refreshExpiresAt);
    }

    /** Token rotation: the old refresh token is dead the moment the new pair lands. */
    @NonNull
    public Session withTokens(@NonNull String newAccessToken,
                              @NonNull String newRefreshToken,
                              @NonNull Instant newAccessExpiresAt,
                              @NonNull Instant newRefreshExpiresAt) {
        return new Session(userId, username, role, mustChangePassword,
                newAccessToken, newRefreshToken, newAccessExpiresAt, newRefreshExpiresAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Session)) {
            return false;
        }
        Session other = (Session) o;
        return userId == other.userId
                && mustChangePassword == other.mustChangePassword
                && username.equals(other.username)
                && role == other.role
                && accessToken.equals(other.accessToken)
                && refreshToken.equals(other.refreshToken)
                && accessExpiresAt.equals(other.accessExpiresAt)
                && refreshExpiresAt.equals(other.refreshExpiresAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, username, role, mustChangePassword,
                accessToken, refreshToken, accessExpiresAt, refreshExpiresAt);
    }

    /** Never include tokens. */
    @NonNull
    @Override
    public String toString() {
        return "Session{userId=" + userId + ", username='" + username + "', role=" + role
                + ", mustChangePassword=" + mustChangePassword
                + ", accessExpiresAt=" + accessExpiresAt
                + ", refreshExpiresAt=" + refreshExpiresAt + "}";
    }

    @Nullable
    public static Role roleOrEmployee(@Nullable Session session) {
        return session == null ? Role.EMPLOYEE : session.getRole();
    }
}