package com.lpms.core.auth;

import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.lpms.core.error.ApiError;
import com.lpms.data.dto.AuthResponse;

import java.time.Duration;
import java.time.Instant;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Single source of truth for the in-memory session.
 *
 * <p>Owns the refresh mutex: {@link #lockRefresh()} is the only way the OkHttp
 * authenticator may rotate tokens, so N concurrent 401s produce exactly one
 * {@code POST /auth/refresh} and never send a consumed refresh token twice.</p>
 */
@Singleton
public final class SessionManager {

    private static final String TAG = "Session";

    /** Refresh slightly before real expiry so no request flies with a dead token. */
    public static final Duration REFRESH_LEEWAY = Duration.ofSeconds(30);

    private final SessionStore store;
    private final MutableLiveData<Session> session = new MutableLiveData<>();
    private final MutableLiveData<SessionEvent> events = new MutableLiveData<>();
    private final Object refreshLock = new Object();

    @Inject
    public SessionManager(@NonNull SessionStore store) {
        this.store = store;
    }

    /** Loads the persisted session at process start. Call from {@code LpmsApplication}. */
    @MainThread
    public void restore() {
        session.postValue(store.load());
    }

    public LiveData<Session> session() {
        return session;
    }

    /** Consumed once by the Activity that owns session-level navigation. */
    public LiveData<SessionEvent> events() {
        return events;
    }

    @Nullable
    public Session current() {
        return session.getValue();
    }

    public boolean isAuthenticated() {
        Session s = session.getValue();
        return s != null && !s.getAccessToken().isEmpty();
    }

    @AnyThread
    public boolean hasRefreshToken() {
        Session s = session.getValue();
        return s != null && !s.getRefreshToken().isEmpty();
    }

    /** True when the access token is gone or within the refresh leeway. */
    @AnyThread
    public boolean needsRefresh() {
        Session s = session.getValue();
        return s == null || s.isAccessTokenExpired(REFRESH_LEEWAY);
    }

    @NonNull
    public Object lockRefresh() {
        return refreshLock;
    }

    // ------------------------------------------------------------- transitions

    /** After a successful login. Must-change-password state comes from {@code /auth/me}. */
    public void onLogin(@NonNull AuthResponse response, boolean mustChangePassword) {
        Session next = toSession(response, mustChangePassword);
        // Persist before publishing so a process death right after login cannot
        // leave the UI authenticated with tokens that are not on disk.
        store.save(next);
        session.postValue(next);
    }

    /**
     * Persists a rotated token pair. Called by the authenticator and MUST complete
     * before the original request is retried.
     */
    public void onTokensRotated(@NonNull Session rotated) {
        store.saveTokens(rotated);
        session.postValue(rotated);
    }

    /** After {@code GET /auth/me}: role/username/mustChangePassword may have changed. */
    public void onProfileRefreshed(long userId,
                                   @NonNull String username,
                                   @NonNull Role role,
                                   boolean mustChangePassword) {
        Session current = session.getValue();
        if (current == null) {
            return;
        }
        Session updated = current.withProfile(userId, username, role, mustChangePassword);
        store.saveProfile(userId, username, role, mustChangePassword);
        session.postValue(updated);
        events.postValue(SessionEvent.of(SessionEvent.Type.PROFILE_CHANGED));
    }

    public void markMustChangePassword(boolean required) {
        Session current = session.getValue();
        if (current == null || current.isMustChangePassword() == required) {
            return;
        }
        Session updated = current.withProfile(current.getUserId(), current.getUsername(),
                current.getRole(), required);
        store.saveProfile(updated.getUserId(), updated.getUsername(), updated.getRole(), required);
        session.postValue(updated);
    }

    /** Change-password succeeded: the server invalidated all tokens, so start clean. */
    public void onPasswordChangedAndTokensInvalidated() {
        clearInternal();
        events.postValue(SessionEvent.of(SessionEvent.Type.LOGIN_REQUIRED,
                "password_changed"));
    }

    public void logout() {
        clearInternal();
        events.postValue(SessionEvent.of(SessionEvent.Type.LOGGED_OUT));
    }

    /** Refresh failed for good: wipe everything and send the user to Login. */
    public void onSessionUnrecoverable(@NonNull ApiError error) {
        Log.w(TAG, "Session unrecoverable, code=" + error.getCode());
        clearInternal();
        events.postValue(SessionEvent.of(SessionEvent.Type.SESSION_EXPIRED, error));
    }

    /** 401 ACCOUNT_DISABLED observed mid-session. */
    public void onAccountDisabled(@NonNull ApiError error) {
        clearInternal();
        events.postValue(SessionEvent.of(SessionEvent.Type.SESSION_EXPIRED, error));
    }

    public void requireLogin(@NonNull SessionEvent.Type type, @Nullable ApiError error) {
        events.postValue(SessionEvent.of(type, error));
    }

    private void clearInternal() {
        store.clear();
        session.postValue(null);
    }

    private static Session toSession(@NonNull AuthResponse response, boolean mustChangePassword) {
        Instant now = Instant.now();
        return new Session(
                response.getUser() == null ? 0L : response.getUser().getId(),
                response.getUser() == null ? "" : safe(response.getUser().getUsername()),
                response.getUser() == null
                        ? Role.EMPLOYEE
                        : Role.fromNullable(response.getUser().getRole()),
                mustChangePassword,
                response.getAccessToken(),
                response.getRefreshToken(),
                now.plusSeconds(response.getExpiresIn()),
                now.plusSeconds(response.getRefreshExpiresIn()));
    }

    private static String safe(@Nullable String s) {
        return s == null ? "" : s;
    }
}