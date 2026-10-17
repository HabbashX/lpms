package com.lpms.ui.shell;

import androidx.annotation.NonNull;

import com.lpms.core.auth.Role;
import com.lpms.core.auth.Session;
import com.lpms.core.auth.SessionEvent;
import com.lpms.core.auth.SessionManager;
import com.lpms.data.repo.AuthRepository;

import javax.inject.Inject;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * Owns activity-level concerns: the current session/role and the session events that
 * need navigation (session expired, forced password change, logout).
 *
 * <p>Events live in {@link SessionManager} as a single-slot LiveData, so an event raised
 * while the Activity is stopped (e.g. a background refresh failed) is replayed once when
 * it comes back.</p>
 */
@HiltViewModel
public final class ShellViewModel extends ViewModel {

    private final AuthRepository authRepository;
    private final SessionManager sessionManager;
    private final CompositeDisposable disposables = new CompositeDisposable();

    @Inject
    public ShellViewModel(@NonNull AuthRepository authRepository) {
        this.authRepository = authRepository;
        this.sessionManager = authRepository.sessions();
    }

    @NonNull
    public LiveData<Session> session() {
        return sessionManager.session();
    }

    @NonNull
    public LiveData<SessionEvent> events() {
        return sessionManager.events();
    }

    @NonNull
    public Role currentRole() {
        Session session = sessionManager.current();
        return session == null ? Role.EMPLOYEE : session.getRole();
    }

    public boolean isAuthenticated() {
        return sessionManager.isAuthenticated();
    }

    public boolean mustChangePassword() {
        Session session = sessionManager.current();
        return session != null && session.isMustChangePassword();
    }

    /**
     * Re-reads {@code /auth/me} on resume: roles are re-evaluated server-side on every
     * request, so a demotion, a disablement or a forced password reset must take effect
     * without the user signing out.
     */
    public void onAppResumed() {
        if (!sessionManager.isAuthenticated() || mustChangePassword()) {
            return;
        }
        // In offline mode there is no /auth/me to ask, and a failed round trip on every
        // resume would surface a snackbar the user can do nothing about.
        if (sessionManager.isOffline()) {
            return;
        }
        disposables.add(authRepository.refreshProfile()
                .subscribe(user -> {
                    // Applied by AuthRepository; role-gated UI re-renders off session().
                }, error -> {
                    // 401s are already handled by the authenticator. A transport failure
                    // just means the UI keeps the role it last knew.
                }));
    }

    /**
     * Leaves offline mode and returns to the normal sign-in.
     *
     * <p>Called instead of {@link #signOut()} when the session is local: the logout
     * endpoint does not exist offline and would add a failing round trip to what should be
     * an instant exit.</p>
     */
    public void exitOffline() {
        sessionManager.logout();
    }

    public void signOut() {
        disposables.add(authRepository.logout().subscribe(() -> {
            // SessionManager emits LOGGED_OUT; the Activity navigates to Login.
        }, error -> {
            // logout() completes regardless of the server answer.
        }));
    }

    @Override
    protected void onCleared() {
        disposables.clear();
    }
}