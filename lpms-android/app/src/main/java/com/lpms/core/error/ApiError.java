package com.lpms.core.error;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The single error model of the app. Every failure — HTTP error envelope,
 * transport failure, malformed body — is expressed as an {@code ApiError} and
 * UI reacts to {@link #getCode()}, never to message text.
 */
public final class ApiError {

    private final int status;
    private final String code;
    private final String message;
    private final String path;
    private final List<FieldError> fieldErrors;

    public ApiError(int status,
                    @NonNull String code,
                    @NonNull String message,
                    @Nullable String path,
                    @Nullable List<FieldError> fieldErrors) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.path = path;
        this.fieldErrors = fieldErrors == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(fieldErrors);
    }

    /** HTTP status, or {@code 0} when the failure happened before any response. */
    public int getStatus() {
        return status;
    }

    @NonNull
    public String getCode() {
        return code;
    }

    /** Server-supplied text, safe to show to the user. */
    @NonNull
    public String getMessage() {
        return message;
    }

    @Nullable
    public String getPath() {
        return path;
    }

    /** Empty unless {@code code == VALIDATION_FAILED} and HTTP status is 400. */
    @NonNull
    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }

    public boolean isValidationFailure() {
        return ApiErrorCodes.VALIDATION_FAILED.equals(code) && status == 400;
    }

    public boolean isNetworkFailure() {
        return ApiErrorCodes.NETWORK_UNAVAILABLE.equals(code) || ApiErrorCodes.TIMEOUT.equals(code);
    }

    /** 401 codes that must not be treated as "log the user out" directly. */
    public boolean isAuthProblem() {
        return status == 401;
    }

    /**
     * @return the first validation message for {@code field} (or for a nested child
     * of it), or {@code null} when this error does not mention that field.
     */
    @Nullable
    public String messageFor(@Nullable String field) {
        if (field == null) {
            return null;
        }
        for (FieldError fe : fieldErrors) {
            if (FieldPath.isOrIsUnder(fe.getField(), field)) {
                return fe.getMessage();
            }
        }
        return null;
    }

    /** Same as {@link #messageFor} for a cart line, e.g. {@code items[0].quantity}. */
    @Nullable
    public String messageForItem(int index, @Nullable String leaf) {
        for (FieldError fe : fieldErrors) {
            if (FieldPath.isItemField(fe.getField(), index, leaf)) {
                return fe.getMessage();
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- factories

    public static ApiError of(int status, @NonNull String code, @NonNull String message) {
        return new ApiError(status, code, message, null, null);
    }

    public static ApiError network(@NonNull String message) {
        return new ApiError(0, ApiErrorCodes.NETWORK_UNAVAILABLE, message, null, null);
    }

    public static ApiError timeout(@NonNull String message) {
        return new ApiError(0, ApiErrorCodes.TIMEOUT, message, null, null);
    }

    /** Fallback when the backend returned a body we could not parse. */
    public static ApiError unparseable(int status, @Nullable String rawBody) {
        String code = status >= 500 ? ApiErrorCodes.INTERNAL_ERROR : ApiErrorCodes.UNKNOWN;
        return new ApiError(status, code, defaultMessageFor(status), null, null);
    }

    private static String defaultMessageFor(int status) {
        if (status == 404) {
            return "Not found";
        }
        if (status == 403) {
            return "You don't have permission";
        }
        if (status == 401) {
            return "Your session has expired. Please sign in again.";
        }
        if (status == 429) {
            return "Too many attempts. Please wait a moment.";
        }
        if (status == 423) {
            return "Account is locked.";
        }
        if (status >= 500) {
            return "Server error. Please try again.";
        }
        return "Request failed (" + status + ")";
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "ApiError{%d %s @%s: %s}",
                status, code, path == null ? "-" : path, message);
    }
}