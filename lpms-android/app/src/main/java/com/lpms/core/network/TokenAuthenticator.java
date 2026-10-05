package com.lpms.core.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.core.auth.Session;
import com.lpms.core.auth.SessionEvent;
import com.lpms.core.auth.SessionManager;
import com.lpms.core.auth.TokenRefresher;
import com.lpms.core.error.ApiError;
import com.lpms.core.error.ApiErrorCodes;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

/**
 * The single place tokens are refreshed.
 *
 * <p>Rules implemented here, in order:</p>
 * <ol>
 *   <li>Only a 401 whose {@code code} is {@code INVALID_TOKEN} /
 *       {@code TOKEN_REVOKED} / {@code UNAUTHORIZED} triggers a refresh.
 *       {@code ACCOUNT_DISABLED} and {@code INVALID_REFRESH_TOKEN} deliberately do
 *       not — they are terminal.</li>
 *   <li>Refresh happens at most once per request: the original call is retried with
 *       the new access token and never re-authenticated a second time.</li>
 *   <li>A shared monitor makes concurrent 401s wait for the same refresh. The token
 *       that failed is compared against the currently stored one; if another thread
 *       already rotated it, that thread simply retries with the fresh token instead
 *       of sending the (now dead) refresh token a second time — reusing it would
 *       make the server revoke every session of that user.</li>
 *   <li>The rotated pair is persisted <em>before</em> the retry is returned.</li>
 *   <li>A rejected refresh (4xx, e.g. {@code INVALID_REFRESH_TOKEN}) wipes the
 *       session and routes to Login.</li>
 * </ol>
 */
@Singleton
public final class TokenAuthenticator implements Authenticator {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER = "Bearer ";
    private static final int MAX_ATTEMPTS = 2;

    private final SessionManager sessionManager;
    private final TokenRefresher refresher;

    @Inject
    public TokenAuthenticator(@NonNull SessionManager sessionManager,
                              @NonNull TokenRefresher refresher) {
        this.sessionManager = sessionManager;
        this.refresher = refresher;
    }

    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, @NonNull Response response) {
        Request failed = response.request();

        // Never re-authenticate the public calls, and never loop.
        if (PublicEndpoints.isPublic(failed) || responseCount(response) >= MAX_ATTEMPTS) {
            return null;
        }

        String code = HttpJson.peekErrorCode(response);
        if (code == null || !ApiErrorCodes.isRefreshTrigger(code)) {
            // 403 PASSWORD_CHANGE_REQUIRED and 401 ACCOUNT_DISABLED land here on
            // purpose: they must surface to the UI, not be "fixed" by a refresh.
            return null;
        }

        String rejectedToken = bearerOf(failed);

        synchronized (sessionManager.lockRefresh()) {
            Session current = sessionManager.current();
            if (current == null) {
                return null;
            }

            if (current.getRefreshToken().isEmpty() || current.isRefreshTokenExpired()) {
                sessionManager.onSessionUnrecoverable(
                        ApiError.of(401, ApiErrorCodes.INVALID_REFRESH_TOKEN,
                                "Your session has expired. Please sign in again."));
                return null;
            }

            if (!rejectedToken.equals(BEARER + current.getAccessToken())) {
                // Another thread already rotated the pair while this request waited.
                return retryWith(failed, current.getAccessToken());
            }

            TokenRefresher.Outcome outcome = refresher.refresh(current.getRefreshToken());
            if (!outcome.isSuccess()) {
                handleRefreshFailure(outcome.requireError(), code);
                return null;
            }

            Session rotated = outcome.getRotatedSession();
            // Preserve the cached profile; the refresh response need not carry it.
            Session merged = rotated.withProfile(
                    current.getUserId(),
                    current.getUsername(),
                    current.getRole(),
                    current.isMustChangePassword());

            // PERSIST FIRST. The old refresh token is already dead server-side, so
            // the new pair must reach disk before anything is retried with it.
            sessionManager.onTokensRotated(merged);

            return retryWith(failed, merged.getAccessToken());
        }
    }

    @Nullable
    private Request retryWith(@NonNull Request original, @NonNull String accessToken) {
        return original.newBuilder()
                .header(AUTHORIZATION, BEARER + accessToken)
                .build();
    }

    private void handleRefreshFailure(@NonNull ApiError refreshError, @NonNull String originalCode) {
        if (refreshError.isNetworkFailure()) {
            // The refresh token is still valid: the server was simply unreachable or
            // too slow. Do NOT wipe the session — just fail this call so the UI can
            // offer a retry, and let the next attempt refresh again.
            return;
        }
        if (ApiErrorCodes.ACCOUNT_DISABLED.equals(refreshError.getCode())) {
            sessionManager.onAccountDisabled(refreshError);
            return;
        }
        if (ApiErrorCodes.ACCOUNT_LOCKED.equals(refreshError.getCode())
                || ApiErrorCodes.RATE_LIMITED.equals(refreshError.getCode())) {
            sessionManager.requireLogin(SessionEvent.Type.SESSION_EXPIRED, refreshError);
            return;
        }
        // Anything else that answers a refresh with a non-2xx (INVALID_REFRESH_TOKEN,
        // 403, 404) means the rotated token is unusable: wipe and go to Login.
        sessionManager.onSessionUnrecoverable(
                refreshError.getCode().equals(originalCode)
                        ? refreshError
                        : ApiError.of(401, ApiErrorCodes.INVALID_REFRESH_TOKEN,
                        refreshError.getMessage()));
    }

    private static int responseCount(@NonNull Response response) {
        int count = 1;
        Response prior = response.priorResponse();
        while (prior != null) {
            count++;
            prior = prior.priorResponse();
        }
        return count;
    }

    @NonNull
    private static String bearerOf(@NonNull Request request) {
        String header = request.header(AUTHORIZATION);
        return header == null ? "" : header;
    }
}