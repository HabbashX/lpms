package com.lpms.ui.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.R;
import com.lpms.core.auth.SessionManager;
import com.lpms.core.error.ApiError;
import com.lpms.core.error.ApiErrorCodes;
import com.lpms.core.network.NetworkCall;
import com.lpms.core.util.AppPreferences;
import com.lpms.data.repo.AuthRepository;

import javax.inject.Inject;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * Login screen logic: credential submission and the error handling the contract calls
 * out explicitly ({@code INVALID_CREDENTIALS}, {@code ACCOUNT_LOCKED},
 * {@code ACCOUNT_DISABLED}, {@code RATE_LIMITED}).
 *
 * <p>There is no server-URL field: the app talks to one fixed Railway endpoint.</p>
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

        LoginError(@NonNull ApiError error) {
            this.error = error;
            this.rateLimited = ApiErrorCodes.RATE_LIMITED.equals(error.getCode());
        }

        @NonNull
        public ApiError getError() {
            return error;
        }

        /** True when the sign-in button must stay disabled until the countdown ends. */
        public boolean isRateLimited() {
            return rateLimited;
        }
    }

    /** Login is rate limited to 10 attempts per 5 minutes per ip+username. */
    private static final long RATE_LIMIT_WINDOW_SECONDS = 300L;

    private final AuthRepository authRepository;
    private final SessionManager sessionManager;
    private final AppPreferences preferences;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Next> next = new MutableLiveData<>();
    private final MutableLiveData<LoginError> error = new MutableLiveData<>();
    private final MutableLiveData<Integer> connectivityHintRes = new MutableLiveData<>();
    private final MutableLiveData<Long> rateLimitCountdown = new MutableLiveData<>(0L);

    @Inject
    public LoginViewModel(@NonNull AuthRepository authRepository,
                          @NonNull AppPreferences preferences) {
        this.authRepository = authRepository;
        this.sessionManager = authRepository.sessions();
        this.preferences = preferences;
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

    /**
     * String resource explaining a transport failure: the deployed server may be asleep
     * (Railway cold start) while the device itself is online. Null otherwise. A resource
     * id is emitted because ViewModels have no Context.
     */
    @NonNull
    public LiveData<Integer> connectivityHintRes() {
        return connectivityHintRes;
    }

    /** Seconds remaining before the submit button may be enabled again; 0 = enabled. */
    @NonNull
    public LiveData<Long> rateLimitCountdown() {
        return rateLimitCountdown;
    }

    /** Submits credentials. No-ops while a request is in flight or rate limited. */
    public void login(@NonNull String username, @NonNull String password) {
        if (Boolean.TRUE.equals(loading.getValue()) || rateLimitCountdownSeconds() > 0L) {
            return;
        }
        if (username.trim().isEmpty() || password.isEmpty()) {
            error.setValue(new LoginError(ApiError.of(400,
                    ApiErrorCodes.VALIDATION_FAILED, "Enter your username and password")));
            return;
        }

        loading.setValue(true);
        error.setValue(null);
        connectivityHintRes.setValue(null);
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

    /**
     * Signs in to offline mode, bypassing the backend entirely.
     *
     * <p>Deliberately does not look at the username or password fields. Offline mode is a
     * preview with an empty local database, so there is nothing to authenticate against -
     * asking for a password it will not check would be worse than not asking.</p>
     */
    public void loginOffline() {
        loading.setValue(true);
        error.setValue(null);
        connectivityHintRes.setValue(null);
        rateLimitCountdown.setValue(0L);
        preferences.setAppMode(AppPreferences.MODE_OFFLINE);
        sessionManager.onOfflineLogin();
        loading.setValue(false);
        next.setValue(Next.HOME);
    }

    /** Leaves offline mode and returns to the normal backend login. */
    public void switchToOnline() {
        preferences.setAppMode(AppPreferences.MODE_ONLINE);
        sessionManager.logout();
    }

    private void onLoginFailed(@NonNull Throwable throwable) {
        ApiError apiError = NetworkCall.asApiError(throwable);
        LoginError loginError = new LoginError(apiError);
        error.setValue(loginError);
        if (loginError.isRateLimited()) {
            startRateLimitCountdown();
            return;
        }
        if (apiError.isNetworkFailure()) {
            if (preferences.isOffline()) {
                // Offline mode was chosen on this screen; probing the server would both
                // fail and tell the user to check their connection, which is exactly the
                // advice that does not apply here.
                return;
            }
            // Distinguish "the server is asleep/unreachable" from "this device is
            // offline" by probing the public health endpoint once.
            probeServerReachability();
        }
    }

    private void probeServerReachability() {
        disposables.add(authRepository.checkHealth()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        health -> connectivityHintRes.setValue(health.isUp()
                                ? R.string.error_server_reachable
                                : R.string.error_server_waking),
                        throwable ->
                                connectivityHintRes.setValue(R.string.error_server_unreachable)));
    }

    private void startRateLimitCountdown() {
        rateLimitCountdown.setValue(RATE_LIMIT_WINDOW_SECONDS);
        disposables.add(io.reactivex.rxjava3.core.Observable
                .interval(1, 1, java.util.concurrent.TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(tick -> {
                    long left = rateLimitCountdownSeconds() - tick;
                    rateLimitCountdown.postValue(Math.max(0L, left));
                }, throwable -> rateLimitCountdown.postValue(0L)));
    }

    private long rateLimitCountdownSeconds() {
        Long value = rateLimitCountdown.getValue();
        return value == null ? 0L : value;
    }

    @Nullable
    public String currentUsername() {
        return sessionManager.current() == null ? null : sessionManager.current().getUsername();
    }

    /**
     * Human-readable form of a 423 lock. The backend puts the remaining time in the
     * message ("Account locked. Try again in 15 minutes."), so it is shown verbatim
     * instead of being re-derived locally.
     */
    @NonNull
    public static String lockHint(@NonNull ApiError error) {
        return error.getMessage();
    }

    @Override
    protected void onCleared() {
        disposables.clear();
    }
}