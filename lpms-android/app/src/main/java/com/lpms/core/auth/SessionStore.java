package com.lpms.core.auth;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.time.Instant;

/**
 * Token persistence backed by {@link EncryptedSharedPreferences} (AES-256-GCM via
 * Tink). Plain preferences are never used for credentials.
 *
 * <p>Persisting a rotated token pair is done through a single
 * {@link #save(Session)} call that commits the access token, refresh token and both
 * expiries together — a refresh must never leave a half-written pair on disk,
 * because the old refresh token is dead the instant the new one is issued.</p>
 */
public final class SessionStore {

    private static final String FILE = "lpms_secure_session";

    private static final String K_USER_ID = "user_id";
    private static final String K_USERNAME = "username";
    private static final String K_ROLE = "role";
    private static final String K_MUST_CHANGE = "must_change_password";
    private static final String K_ACCESS_TOKEN = "access_token";
    private static final String K_REFRESH_TOKEN = "refresh_token";
    private static final String K_ACCESS_EXPIRES = "access_expires_at";
    private static final String K_REFRESH_EXPIRES = "refresh_expires_at";

    /**
     * Stands in for an expiry that was never written. Far enough ahead to never look
     * expired, but a normal value so arithmetic on it cannot overflow.
     */
    private static final Instant UNKNOWN_EXPIRY =
            Instant.parse("2999-01-01T00:00:00Z");

    private final SharedPreferences prefs;

    public SessionStore(@NonNull Context context) {
        this.prefs = create(context.getApplicationContext());
    }

    private static SharedPreferences create(Context appContext) {
        try {
            MasterKey masterKey = new MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    appContext,
                    FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            // Keyset can be unreadable after a device restore or an app reinstall with
            // stale keys. Fall back to empty storage rather than crashing on launch;
            // the user simply signs in again.
            return appContext.getSharedPreferences(FILE + "_fallback", Context.MODE_PRIVATE);
        }
    }

    /** Atomically persists the whole session, including a rotated token pair. */
    public void save(@NonNull Session session) {
        // commit(), not apply(): the previous refresh token is already dead server-side
        // the moment a new pair is issued, so a write still sitting in the async queue
        // would leave a dead token on disk if the process is killed. Next launch would
        // replay it, and the backend treats a replayed rotated token as theft and
        // revokes every session - signing the user out for backgrounding the app.
        prefs.edit()
                .putLong(K_USER_ID, session.getUserId())
                .putString(K_USERNAME, session.getUsername())
                .putString(K_ROLE, session.getRole().name())
                .putBoolean(K_MUST_CHANGE, session.isMustChangePassword())
                .putString(K_ACCESS_TOKEN, session.getAccessToken())
                .putString(K_REFRESH_TOKEN, session.getRefreshToken())
                .putLong(K_ACCESS_EXPIRES, session.getAccessExpiresAt().toEpochMilli())
                .putLong(K_REFRESH_EXPIRES, session.getRefreshExpiresAt().toEpochMilli())
                .commit();
    }

    /** Persists only the rotated tokens/expiries, leaving the cached profile intact. */
    public void saveTokens(@NonNull Session session) {
        // commit() for the same reason as save(): a rotated refresh token must be on disk
        // before the dead one can still be read back.
        prefs.edit()
                .putString(K_ACCESS_TOKEN, session.getAccessToken())
                .putString(K_REFRESH_TOKEN, session.getRefreshToken())
                .putLong(K_ACCESS_EXPIRES, session.getAccessExpiresAt().toEpochMilli())
                .putLong(K_REFRESH_EXPIRES, session.getRefreshExpiresAt().toEpochMilli())
                .commit();
    }

    /** Persists role/mustChangePassword after {@code GET /auth/me}. */
    public void saveProfile(long userId,
                            @NonNull String username,
                            @NonNull Role role,
                            boolean mustChangePassword) {
        prefs.edit()
                .putLong(K_USER_ID, userId)
                .putString(K_USERNAME, username)
                .putString(K_ROLE, role.name())
                .putBoolean(K_MUST_CHANGE, mustChangePassword)
                .apply();
    }

    /** @return the stored session, or null when nothing usable is stored. */
    @Nullable
    public Session load() {
        String access = prefs.getString(K_ACCESS_TOKEN, null);
        String refresh = prefs.getString(K_REFRESH_TOKEN, null);
        if (isBlank(access) || isBlank(refresh)) {
            return null;
        }
        long userId = prefs.getLong(K_USER_ID, 0L);
        String username = prefs.getString(K_USERNAME, "");
        Role role = Role.fromNullable(prefs.getString(K_ROLE, null));
        boolean mustChange = prefs.getBoolean(K_MUST_CHANGE, false);
        long accessExp = prefs.getLong(K_ACCESS_EXPIRES, 0L);
        long refreshExp = prefs.getLong(K_REFRESH_EXPIRES, 0L);
        // A missing expiry means "unknown", not "1970". Treating it as epoch zero makes
        // the session look expired and signs the user out instead of letting the refresh
        // attempt decide - the server is the authority on whether a token is still good.
        return new Session(
                userId,
                username == null ? "" : username,
                role,
                mustChange,
                access,
                refresh,
                accessExp > 0L ? Instant.ofEpochMilli(accessExp) : UNKNOWN_EXPIRY,
                refreshExp > 0L ? Instant.ofEpochMilli(refreshExp) : UNKNOWN_EXPIRY);
    }

    /** Wipes every trace of the session (tokens, expiries, cached profile). */
    public void clear() {
        prefs.edit().clear().apply();
    }

    /** True when a refresh token exists at all; used to decide whether to try refresh. */
    public boolean hasRefreshToken() {
        return !isBlank(prefs.getString(K_REFRESH_TOKEN, null));
    }

    private static boolean isBlank(@Nullable String s) {
        return s == null || s.trim().isEmpty();
    }
}