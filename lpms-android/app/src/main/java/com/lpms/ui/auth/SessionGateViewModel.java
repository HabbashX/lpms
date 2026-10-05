package com.lpms.ui.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.core.auth.Session;
import com.lpms.core.auth.SessionManager;
import com.lpms.core.ui.UiState;
import com.lpms.data.repo.AuthRepository;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/**
 * Splash / session gate.
 *
 * <p>Reads the stored token pair, then validates it against {@code /auth/me} and
 * decides the start destination:</p>
 * <ul>
 *   <li>no session → {@link Destination#LOGIN}</li>
 *   <li>session valid, {@code mustChangePassword} → forced change password</li>
 *   <li>session valid → {@link Destination#HOME}</li>
 *   <li>401 that the authenticator could not recover → Login (session already wiped)</li>
 * </ul>
 */
@HiltViewModel
public final class SessionGateViewModel extends ViewModel {

    /** Where the app should start. */
    public enum Destination {
        LOGIN,
        CHANGE_PASSWORD,
        HOME
    }

    private final AuthRepository authRepository;
    private final SessionManager sessionManager;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<UiState<Destination>> state = new MutableLiveData<>();

    @Inject
    public SessionGateViewModel(@NonNull AuthRepository authRepository) {
        this.authRepository = authRepository;
        this.sessionManager = authRepository.sessions();
        state.setValue(UiState.loading());
    }

    @NonNull
    public LiveData<UiState<Destination>> state() {
        return state;
    }

    @NonNull
    public LiveData<Session> session() {
        return sessionManager.session();
    }

    /** Called once when the gate view is created. */
    public void resolve() {
        Session current = sessionManager.current();
        if (current == null || current.getAccessToken().isEmpty()) {
            state.setValue(UiState.content(Destination.LOGIN));
            return;
        }
        // A stored session whose refresh token already expired cannot be renewed: do not
        // spend a round trip on it.
        if (current.isRefreshTokenExpired()) {
            sessionManager.onSessionUnrecoverable(com.lpms.core.error.ApiError.of(
                    401, com.lpms.core.error.ApiErrorCodes.INVALID_REFRESH_TOKEN,
                    "Your session has expired. Please sign in again."));
            state.setValue(UiState.content(Destination.LOGIN));
            return;
        }

        disposables.add(authRepository.refreshProfile()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        user -> state.setValue(UiState.content(user.isMustChangePassword()
                                ? Destination.CHANGE_PASSWORD
                                : Destination.HOME)),
                        error -> state.setValue(resolveAfterError(error))));
    }

    private UiState<Destination> resolveAfterError(@NonNull Throwable error) {
        com.lpms.core.error.ApiError apiError =
                com.lpms.core.network.NetworkCall.asApiError(error);

        // A transport failure is not a session problem: keep the user in the app and let
        // the home screen offer retry rather than bouncing to Login.
        if (apiError.isNetworkFailure()) {
            return UiState.error(apiError);
        }
        if (apiError.getStatus() == 403
                && com.lpms.core.error.ApiErrorCodes.PASSWORD_CHANGE_REQUIRED
                .equals(apiError.getCode())) {
            return UiState.content(Destination.CHANGE_PASSWORD);
        }
        // Any other rejection (401 not refreshable, 404, …): the session layer has
        // already cleared storage where appropriate.
        return UiState.content(Destination.LOGIN);
    }

    @Nullable
    public Session currentSession() {
        return sessionManager.current();
    }

    @Override
    protected void onCleared() {
        disposables.clear();
    }
}
