package com.lpms.data.repo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.core.auth.Role;
import com.lpms.core.auth.Session;
import com.lpms.core.auth.SessionManager;
import com.lpms.core.error.ApiError;
import com.lpms.core.error.ApiErrorCodes;
import com.lpms.core.error.ApiException;
import com.lpms.core.network.NetworkCall;
import com.lpms.data.api.AuthApi;
import com.lpms.data.api.HealthApi;
import com.lpms.data.dto.AuthResponse;
import com.lpms.data.dto.ChangePasswordRequest;
import com.lpms.data.dto.HealthResponse;
import com.lpms.data.dto.LoginRequest;
import com.lpms.data.dto.LogoutRequest;
import com.lpms.data.dto.UserResponse;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Auth + session operations. Together with the OkHttp authenticator, the only code
 * allowed to mutate {@link SessionManager}.
 *
 * <p><b>Never call {@code /auth/refresh} from here.</b> Refresh tokens are single-use
 * and rotated; issuing one here could race with the authenticator and make the server
 * revoke every session of that user.</p>
 */
@Singleton
public final class AuthRepository {

    private final AuthApi authApi;
    private final HealthApi healthApi;
    private final SessionManager sessionManager;

    @Inject
    public AuthRepository(@NonNull AuthApi authApi,
                          @NonNull HealthApi healthApi,
                          @NonNull SessionManager sessionManager) {
        this.authApi = authApi;
        this.healthApi = healthApi;
        this.sessionManager = sessionManager;
    }

    @NonNull
    public SessionManager sessions() {
        return sessionManager;
    }

    /**
     * Public health probe for the login screen's connectivity check. The backend may be
     * on a free-tier host that cold-starts slowly, so the UI shows a "waking up
     * server…" state while this is in flight.
     */
    @NonNull
    public Single<HealthResponse> checkHealth() {
        return healthApi.health().subscribeOn(Schedulers.io());
    }

    /**
     * Logs in, then re-reads {@code /auth/me} so {@code mustChangePassword} and the
     * authoritative role are known before the first real screen appears.
     */
    @NonNull
    public Single<AuthOutcome> login(@NonNull String username, @NonNull String password) {
        return authApi.login(new LoginRequest(username, password))
                .flatMap(response -> {
                    if (!response.isUsable()) {
                        return Single.error(new ApiException(ApiError.of(
                                500, ApiErrorCodes.INTERNAL_ERROR,
                                "The server returned an incomplete sign-in response")));
                    }
                    sessionManager.onLogin(response, false);
                    return authApi.me()
                            .map(user -> {
                                applyProfile(user, username);
                                return new AuthOutcome(user, user.isMustChangePassword());
                            })
                            .onErrorReturn(error -> {
                                // /me is best effort: the token pair is already stored, so a
                                // transient failure here must not block a valid sign-in.
                                Session stored = sessionManager.current();
                                return new AuthOutcome(null,
                                        stored != null && stored.isMustChangePassword());
                            });
                })
                .onErrorResumeNext(error -> Single.error(
                        new ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    /** Re-reads {@code /auth/me}; called on every app resume so role gating stays fresh. */
    @NonNull
    public Single<UserResponse> refreshProfile() {
        return authApi.me()
                .doOnSuccess(user -> applyProfile(user, null))
                .onErrorResumeNext(error -> Single.error(
                        new ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    /**
     * Changes the password. Success invalidates all tokens server-side, so local storage
     * is discarded and the user signs in again with the new password.
     */
    @NonNull
    public Completable changePassword(@NonNull String currentPassword, @NonNull String newPassword) {
        return authApi.changePassword(new ChangePasswordRequest(currentPassword, newPassword))
                .doOnComplete(sessionManager::onPasswordChangedAndTokensInvalidated)
                .onErrorResumeNext(error -> Completable.error(
                        new ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    /**
     * Revokes the access token and, when present, the refresh token as well. Local
     * storage is cleared regardless of the outcome so logout always succeeds for the user.
     */
    @NonNull
    public Completable logout() {
        Session current = sessionManager.current();
        LogoutRequest body = new LogoutRequest(current == null ? null : current.getRefreshToken());
        return authApi.logout(body)
                .doOnComplete(sessionManager::logout)
                .onErrorComplete(throwable -> {
                    // Local storage is cleared even when the server is unreachable, so the
                    // user is never left in a half-signed-in state.
                    sessionManager.logout();
                    return true;
                })
                .subscribeOn(Schedulers.io());
    }

    private void applyProfile(@Nullable UserResponse user, @Nullable String fallbackUsername) {
        if (user == null) {
            return;
        }
        sessionManager.onProfileRefreshed(
                user.getId() == null ? 0L : user.getId(),
                user.getUsername() == null ? (fallbackUsername == null ? "" : fallbackUsername)
                        : user.getUsername(),
                Role.fromNullable(user.getRole()),
                user.isMustChangePassword());
    }

    /** Result of a successful login. */
    public static final class AuthOutcome {

        private final UserResponse user;
        private final boolean mustChangePassword;

        AuthOutcome(@Nullable UserResponse user, boolean mustChangePassword) {
            this.user = user;
            this.mustChangePassword = mustChangePassword;
        }

        @Nullable
        public UserResponse getUser() {
            return user;
        }

        public boolean isMustChangePassword() {
            return mustChangePassword;
        }
    }
}