package com.lpms.core.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.core.error.ApiError;

import java.time.Instant;

/**
 * Performs {@code POST /auth/refresh} off the main OkHttp client so the refresh
 * itself can never recurse back into {@link com.lpms.core.network.TokenAuthenticator}.
 */
public interface TokenRefresher {

    /**
     * Result of one refresh attempt. The server rotates both tokens on success, so
     * a successful result always carries a brand-new pair.
     */
    final class Outcome {

        private final Session rotatedSession;
        private final ApiError error;

        private Outcome(@Nullable Session rotatedSession, @Nullable ApiError error) {
            this.rotatedSession = rotatedSession;
            this.error = error;
        }

        public static Outcome success(@NonNull Session rotated) {
            return new Outcome(rotated, null);
        }

        public static Outcome failure(@NonNull ApiError error) {
            return new Outcome(null, error);
        }

        public boolean isSuccess() {
            return rotatedSession != null;
        }

        @Nullable
        public Session getRotatedSession() {
            return rotatedSession;
        }

        @NonNull
        public ApiError requireError() {
            if (error == null) {
                throw new IllegalStateException("Successful refresh outcome has no error");
            }
            return error;
        }
    }

    /**
     * Exchanges {@code refreshToken} for a new pair. Implementations MUST NOT retry:
     * the refresh token is single-use, so a second attempt with the same value makes
     * the server revoke every session of that user.
     */
    @NonNull
    Outcome refresh(@NonNull String refreshToken);

    /** Expiry helper shared by the refresher and the session gate. */
    static Instant expiryFromNow(long seconds) {
        return Instant.now().plusSeconds(Math.max(0L, seconds));
    }
}