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
 * <p>The session lives in a plain {@code volatile} field, <b>not</b> in the LiveData.
 * LiveData's {@code postValue} is asynchronous, which previously let two callers race:
 * {@link #restore()} could lose to the session gate (so every launch looked logged out),
 * and {@link #onLogin} could lose to the {@code /auth/me} call that immediately follows
 * it (so that request went out with no {@code Authorization} header). The field is read
 * synchronously from any thread; the LiveData exists only to notify observers.</p>
 *
 * <p>Also owns the refresh mutex: {@link #lockRefresh()} is the only way the OkHttp
 * authenticator may rotate tokens, so concurrent 401s produce one
 * {@code POST /auth/refresh} instead of several competing uses of a single-use token.</p>
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

    /** Synchronous, thread-safe source of truth. */
    @Nullable
    private volatile Session current;

    @Inject
    public SessionManager(@NonNull SessionStore store) {
        this.store = store;
    }

    /**
     * Loads the persisted session at process start. Synchronous, so the session gate can
     * read it immediately without racing the LiveData delivery.
     */
    @MainThread
    public void restore() {
        Session loaded = store.load();
        current = loaded;
        session.setValue(loaded);
    }

    public LiveData<Session> session() {
        return session;
    }

    /** Consumed by the Activity that owns session-level navigation. */
    public LiveData<SessionEvent> events() {
        return events;
    }

    @Nullable
    public Session current() {
        return current;
    }

    public boolean isAuthenticated() {
        Session s = current;
        return s != null && !s.getAccessToken().isEmpty();
    }

    @AnyThread
    public boolean hasRefreshToken() {
        Session s = current;
        return s != null && !s.getRefreshToken().isEmpty();
    }

    /** True when the access token is gone or within the refresh leeway. */
    @AnyThread
    public boolean needsRefresh() {
        Session s = current;
        return s == null || s.isAccessTokenExpired(REFRESH_LEEWAY);
    }

    @NonNull
    public Object lockRefresh() {
        return refreshLock;
    }

    /**
     * Enters offline mode.
     *
     * <p>No credentials are checked and no request is made: the point of offline mode is to
     * work with a phone that cannot reach the pharmacy's server. The tokens are markers
     * rather than real ones - {@link #isAuthenticated()} only tests that they are present,
     * and nothing ever sends them because no repository calls the network in this mode.</p>
     *
     * <p>The role is deliberately {@link Role#ADMIN}. A preview handed to a customer should
     * show the whole product, and a role that hides half the menus would make the demo
     * look like the app is broken. This session never touches real data.</p>
     */
    public void onOfflineLogin() {
        // Far future, so no refresh path is ever entered and the session outlives a demo.
        Instant far = Instant.now().plusSeconds(60L * 60 * 24 * 3650);
        Session next = new Session(
                OFFLINE_USER_ID,
                OFFLINE_USERNAME,
                Role.ADMIN,
                false,
                OFFLINE_ACCESS_TOKEN,
                OFFLINE_REFRESH_TOKEN,
                far,
                far);
        // commit() not apply(): the mode must be on disk before any screen reads it, or a
        // process death mid-launch would drop the user back to a login screen that then
        // cannot start offline mode from a cleared auth state.
        store.save(next);
        publish(next);
    }

    /** True when the current session belongs to offline mode. */
    public boolean isOffline() {
        Session s = current;
        return s != null && OFFLINE_ACCESS_TOKEN.equals(s.getAccessToken());
    }

    /** Id of the local user shown in the More hub. */
    public static final long OFFLINE_USER_ID = -1L;

    /** Marker tokens; recognised by {@link #isOffline()}. */
    public static final String OFFLINE_ACCESS_TOKEN = "offline-mode";
    public static final String OFFLINE_REFRESH_TOKEN = "offline-mode";

    /** Username shown for the local session. */
    private static final String OFFLINE_USERNAME = "Preview user";

    // ------------------------------------------------------------- transitions

    /** After a successful login. {@code mustChangePassword} comes from /auth/me. */
    public void onLogin(@NonNull AuthResponse response, boolean mustChangePassword) {
        Session next = toSession(response, mustChangePassword);
        // Persist before publishing: a process death right after login must not leave the
        // UI authenticated with tokens that are not on disk.
        store.save(next);
        publish(next);
    }

    /**
     * Persists a rotated token pair. Called by the authenticator and MUST complete
     * before the original request is retried.
     */
    public void onTokensRotated(@NonNull Session rotated) {
        store.saveTokens(rotated);
        publish(rotated);
    }

    /** After {@code GET /auth/me}: role/username/mustChangePassword may have changed. */
    public void onProfileRefreshed(long userId,
                                   @NonNull String username,
                                   @NonNull Role role,
                                   boolean mustChangePassword) {
        Session existing = current;
        if (existing == null) {
            return;
        }
        Session updated = existing.withProfile(userId, username, role, mustChangePassword);
        store.saveProfile(userId, username, role, mustChangePassword);
        publish(updated);
        events.postValue(SessionEvent.of(SessionEvent.Type.PROFILE_CHANGED));
    }

    public void markMustChangePassword(boolean required) {
        Session existing = current;
        if (existing == null || existing.isMustChangePassword() == required) {
            return;
        }
        Session updated = existing.withProfile(existing.getUserId(), existing.getUsername(),
                existing.getRole(), required);
        store.saveProfile(updated.getUserId(), updated.getUsername(), updated.getRole(), required);
        publish(updated);
    }

    /** Change-password succeeded: the server invalidated all tokens, so start clean. */
    public void onPasswordChangedAndTokensInvalidated() {
        clearInternal();
        events.postValue(SessionEvent.of(SessionEvent.Type.LOGIN_REQUIRED, "password_changed"));
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

    private void publish(@Nullable Session value) {
        current = value;
        // Observers are notified on the main thread; readers use the field above.
        session.postValue(value);
    }

    private void clearInternal() {
        store.clear();
        current = null;
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