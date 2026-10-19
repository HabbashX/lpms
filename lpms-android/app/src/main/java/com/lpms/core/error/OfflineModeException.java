package com.lpms.core.error;

import androidx.annotation.NonNull;

import java.io.IOException;

/**
 * Thrown instead of sending a request while the app is in offline mode.
 *
 * <p>It is an {@link IOException} so it travels like any other transport failure, but it
 * must not be reported to the user as one: retrying cannot help, and "check your
 * network" is the wrong advice when the app is deliberately running without a
 * server. {@link ApiErrorMapper} turns it into {@link ApiErrorCodes#OFFLINE_MODE},
 * which says what is actually true.</p>
 */
public final class OfflineModeException extends IOException {

    private final String path;

    public OfflineModeException(@NonNull String path) {
        super("Offline mode: " + path + " is not available on this device");
        this.path = path;
    }

    /** The request path that was blocked, for logging only. */
    @NonNull
    public String getPath() {
        return path;
    }
}