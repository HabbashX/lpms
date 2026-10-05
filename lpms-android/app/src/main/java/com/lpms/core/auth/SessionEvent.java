package com.lpms.core.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.core.error.ApiError;

/**
 * One-shot navigation signals raised from anywhere in the stack (OkHttp
 * authenticator, repository, Activity) that the session layer must act on.
 */
public final class SessionEvent {

    public enum Type {
        /** No usable session: show Login. */
        LOGIN_REQUIRED,
        /** Refresh token rejected / rotated away: tokens wiped, show Login. */
        SESSION_EXPIRED,
        /** Logout completed by the user. */
        LOGGED_OUT,
        /** 403 PASSWORD_CHANGE_REQUIRED or mustChangePassword=true: forced screen. */
        PASSWORD_CHANGE_REQUIRED,
        /** Role or enabled state changed on the server; UI must re-gate. */
        PROFILE_CHANGED
    }

    private final Type type;
    private final String reason;
    private final ApiError error;

    private SessionEvent(@NonNull Type type, @Nullable String reason, @Nullable ApiError error) {
        this.type = type;
        this.reason = reason;
        this.error = error;
    }

    @NonNull
    public Type getType() {
        return type;
    }

    @Nullable
    public String getReason() {
        return reason;
    }

    @Nullable
    public ApiError getError() {
        return error;
    }

    @NonNull
    public static SessionEvent of(@NonNull Type type) {
        return new SessionEvent(type, null, null);
    }

    @NonNull
    public static SessionEvent of(@NonNull Type type, @Nullable String reason) {
        return new SessionEvent(type, reason, null);
    }

    @NonNull
    public static SessionEvent of(@NonNull Type type, @Nullable ApiError error) {
        return new SessionEvent(type,
                error == null ? null : error.getMessage(),
                error);
    }

    @NonNull
    @Override
    public String toString() {
        return "SessionEvent{" + type + (reason == null ? "" : ": " + reason) + "}";
    }
}