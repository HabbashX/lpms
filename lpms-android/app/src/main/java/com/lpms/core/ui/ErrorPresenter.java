package com.lpms.core.ui;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.lpms.R;
import com.lpms.core.error.ApiError;
import com.lpms.core.error.ApiErrorCodes;

/**
 * Turns an {@link ApiError} into text plus a coarse {@link Action} the UI reacts to.
 *
 * <p>The backend {@code message} is preferred because it is already localised-safe and
 * specific ("Insufficient stock for Amoxcillin"), with an English fallback for the codes
 * that need friendlier wording. <b>Branch on {@code code}, never on message text.</b></p>
 */
public final class ErrorPresenter {

    /** Coarse, UI-level consequence of an error. */
    public enum Action {
        /** Show inline on the form field. */
        FIELD,
        /** Show a snackbar with the server message. */
        SNACKBAR,
        /** Navigate to Login (session unrecoverable). */
        GO_TO_LOGIN,
        /** Forced, non-dismissable change-password screen. */
        FORCE_PASSWORD_CHANGE,
        /** Offer a retry button (transport / 5xx). */
        RETRY
    }

    private ErrorPresenter() {
    }

    @NonNull
    public static Action actionFor(@NonNull ApiError error) {
        String code = error.getCode();
        if (ApiErrorCodes.PASSWORD_CHANGE_REQUIRED.equals(code)) {
            return Action.FORCE_PASSWORD_CHANGE;
        }
        if (ApiErrorCodes.VALIDATION_FAILED.equals(code) && !error.getFieldErrors().isEmpty()) {
            return Action.FIELD;
        }
        if (ApiErrorCodes.INVALID_SALE.equals(code)
                || ApiErrorCodes.INVALID_PRICING.equals(code)
                || ApiErrorCodes.INVALID_MARGIN.equals(code)
                || isPasswordFormCode(code)) {
            return Action.SNACKBAR;
        }
        if (error.getStatus() == 401 || error.getStatus() == 403) {
            return Action.GO_TO_LOGIN;
        }
        if (error.isNetworkFailure() || error.getStatus() == 0 || error.getStatus() >= 500) {
            return Action.RETRY;
        }
        return Action.SNACKBAR;
    }

    @NonNull
    public static String message(@NonNull Context context, @NonNull ApiError error) {
        String server = error.getMessage();
        if (server != null && !server.trim().isEmpty()) {
            return server;
        }
        return context.getString(fallbackStringRes(error));
    }

    @StringRes
    public static int fallbackStringRes(@NonNull ApiError error) {
        String code = error.getCode();
        if (ApiErrorCodes.NETWORK_UNAVAILABLE.equals(code)) {
            return R.string.error_network;
        }
        if (ApiErrorCodes.TIMEOUT.equals(code)) {
            return R.string.error_timeout;
        }
        if (ApiErrorCodes.FORBIDDEN.equals(code)) {
            return R.string.error_no_permission;
        }
        if (ApiErrorCodes.NOT_FOUND.equals(code)) {
            return R.string.error_not_found;
        }
        if (ApiErrorCodes.RATE_LIMITED.equals(code)) {
            return R.string.error_rate_limited;
        }
        if (ApiErrorCodes.ACCOUNT_LOCKED.equals(code)) {
            return R.string.error_account_locked;
        }
        if (ApiErrorCodes.ACCOUNT_DISABLED.equals(code)) {
            return R.string.error_account_disabled;
        }
        if (ApiErrorCodes.INVALID_CREDENTIALS.equals(code)) {
            return R.string.error_invalid_credentials;
        }
        if (ApiErrorCodes.INTERNAL_ERROR.equals(code) || error.getStatus() >= 500) {
            return R.string.error_server;
        }
        if (error.isValidationFailure()) {
            return R.string.error_validation;
        }
        if (error.getStatus() == 401) {
            return R.string.error_session_expired;
        }
        return R.string.error_generic;
    }

    /** Validation messages addressed to a form field, else null. */
    @Nullable
    public static String fieldMessage(@NonNull ApiError error, @Nullable String field) {
        return error.messageFor(field);
    }

    private static boolean isPasswordFormCode(@NonNull String code) {
        return ApiErrorCodes.WEAK_PASSWORD.equals(code)
                || ApiErrorCodes.INVALID_CURRENT_PASSWORD.equals(code)
                || ApiErrorCodes.PASSWORD_REUSE.equals(code);
    }
}
