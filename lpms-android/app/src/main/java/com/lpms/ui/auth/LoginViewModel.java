package com.lpms.ui.auth;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.core.auth.SessionManager;
import com.lpms.core.error.ApiError;
import com.lpms.core.error.ApiErrorCodes;
import com.lpms.core.network.NetworkCall;
import com.lpms.core.network.ServerUrl;
import com.lpms.data.repo.AuthRepository;

import javax.inject.Inject;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * Login screen logic: connectivity probe, credential submission and the error handling
 * that the contract calls out explicitly ({@code INVALID_CREDENTIALS},
 * {@code ACCOUNT_LOCKED}, {@code ACCOUNT_DISABLED}, {@code RATE_LIMITED}).
 *
 * <p>Also owns the dev-only server URL override, which is why it needs
 * {@link ServerUrl}.</p>
 */
@HiltViewModel
public final class LoginViewModel extends ViewModel {

    /** What the Fragment should do after a successful sign-in. */
    public enum Next {
        HOME,
        CHANGE_PASSWORD
    }

    /** Terminal UI feedback for a failed attempt. */
    public static final class LoginError {

        private final ApiError error;
        private final boolean rateLimited;
        private final long retryAfterSeconds;

        LoginError(@NonNull ApiError error) {
            this.error = error;
            this.rateLimited = ApiErrorCodes.RATE_LIMITED.equals(error.getCode());
            // 429 gives no Retry-After header in this contract, so fall back to the
            // documented 5-minute window and start the countdown from there.
            this.retryAfterSeconds = RATE_LIMIT_WINDOW_SECONDS;
        }

        @NonNull
        public ApiError getError() {
            return error;
        }

        /** True when the sign-in button must stay disabled until the countdown ends. */
        public boolean isRateLimited() {
            return rateLimited;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    /** Login is rate limited to 10 attempts per 5 minutes per ip+username. */
    private static final long RATE_LIMIT_WINDOW_SECONDS = 300L;

    private final AuthRepository authRepository;
    private final ServerUrl serverUrl;
    private final SessionManager sessionManager;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Next> next = new MutableLiveData<>();
    private final MutableLiveData<LoginError> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> serverReachable = new MutableLiveData<>(false);
    private final MutableLiveData<Long> rateLimitCountdown = new MutableLiveData<>(0L);
    private final MutableLiveData<Boolean> checkingServer = new MutableLiveData<>(false);

    @Inject
    public LoginViewModel(@NonNull AuthRepository authRepository, @NonNull ServerUrl serverUrl) {
        this.authRepository = authRepository;
        this.serverUrl = serverUrl;
        this.sessionManager = authRepository.sessions();
    }

    @NonNull
    public LiveData<Boolean> loading() {
        return loading;
    }

    @NonNull
    public LiveData<Next> next() {
        return next;
    }

    @NonNull
    public LiveData<LoginError> error() {
        return error;
    }

    @NonNull
    public LiveData<Boolean> serverReachable() {
        return serverReachable;
    }

    /** Seconds remaining before the submit button may be enabled again; 0 = enabled. */
    @NonNull
    public LiveData<Long> rateLimitCountdown() {
        return rateLimitCountdown;
    }

    @NonNull
    public LiveData<Boolean> checkingServer() {
        return checkingServer;
    }

    public boolean isServerUrlConfigurable() {
        return com.lpms.BuildConfig.ALLOW_SERVER_URL_OVERRIDE;
    }

    public boolean hasServerUrlOverride() {
        return serverUrl.hasOverride();
    }

    @NonNull
    public String currentServerUrl() {
        return serverUrl.baseUrl();
    }

    @NonNull
    public String buildFlavorBaseUrl() {
        return com.lpms.BuildConfig.BASE_URL;
    }

    /** Applies a dev-only server override. @return false when the URL is unusable. */
    public boolean setServerUrl(@Nullable String candidate) {
        return serverUrl.setOverride(candidate);
    }

    public void resetServerUrl() {
        serverUrl.clearOverride();
    }

    /**
     * Connectivity probe for {@code GET /}. Runs on screen entry so the user learns
     * immediately whether the host is reachable, and again after a URL change.
     */
    public void checkServer() {
        checkingServer.setValue(true);
        disposables.add(authRepository.checkHealth()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        health -> {
                            serverReachable.setValue(true);
                            checkingServer.setValue(false);
                        },
                        throwable -> {
                            serverReachable.setValue(false);
                            checkingServer.setValue(false);
                        }));
    }

    /** Submits credentials. No-ops while a request is in flight or rate limited. */
    public void login(@NonNull String username, @NonNull String password) {
        if (Boolean.TRUE.equals(loading.getValue())) {
            return;
        }
        if (rateLimitCountdownSeconds() > 0L) {
            return;
        }
        if (username.trim().isEmpty() || password.isEmpty()) {
            error.setValue(new LoginError(ApiError.of(400,
                    ApiErrorCodes.VALIDATION_FAILED, "Enter your username and password")));
            return;
        }

        loading.setValue(true);
        error.setValue(null);
        disposables.add(authRepository.login(username.trim(), password)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        outcome -> {
                            loading.setValue(false);
                            next.setValue(outcome.isMustChangePassword()
                                    ? Next.CHANGE_PASSWORD
                                    : Next.HOME);
                        },
                        throwable -> {
                            loading.setValue(false);
                            onLoginFailed(throwable);
                        }));
    }

    private void onLoginFailed(@NonNull Throwable throwable) {
        ApiError apiError = NetworkCall.asApiError(throwable);
        LoginError loginError = new LoginError(apiError);
        error.setValue(loginError);
        if (loginError.isRateLimited()) {
            startRateLimitCountdown();
        }
    }

    private void startRateLimitCountdown() {
        long remaining = RATE_LIMIT_WINDOW_SECONDS;
        rateLimitCountdown.setValue(remaining);
        disposables.add(io.reactivex.rxjava3.core.Observable
                .interval(1, 1, java.util.concurrent.TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(tick -> {
                    long left = rateLimitCountdownSeconds() - tick;
                    if (left <= 0L) {
                        rateLimitCountdown.postValue(0L);
                    } else {
                        rateLimitCountdown.setValue(left);
                    }
                }, throwable -> rateLimitCountdown.postValue(0L)));
    }

    private long rateLimitCountdownSeconds() {
        Long value = rateLimitCountdown.getValue();
        return value == null ? 0L : value;
    }

    /** Seconds left on a 423 lock, for the "try again in …" hint. */
    @NonNull
    public static String lockHint(@NonNull Context context, @NonNull ApiError error) {
        // The backend puts the remaining time in the message ("Account locked. Try again
        // in 15 minutes."); show it verbatim rather than re-deriving it locally.
        return error.getMessage();
    }

    @Nullable
    public String currentUsername() {
        return sessionManager.current() == null ? null : sessionManager.current().getUsername();
    }

    @Override
    protected void onCleared() {
        disposables.clear();
    }
}
