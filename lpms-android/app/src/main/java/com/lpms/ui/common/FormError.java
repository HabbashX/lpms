package com.lpms.ui.common;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * A form error that is either a local string resource or a verbatim server message.
 *
 * <p>ViewModels have no {@link Context}, so they emit one of these and the Fragment
 * resolves it — which also keeps server text (already localised by the backend) intact
 * instead of routing it through a resource.</p>
 */
public final class FormError {

    @StringRes
    private final int stringRes;

    @Nullable
    private final String message;

    private FormError(@StringRes int stringRes, @Nullable String message) {
        this.stringRes = stringRes;
        this.message = message;
    }

    /** Local, translated wording (policy hints, "must not be empty"). */
    @NonNull
    public static FormError of(@StringRes int stringRes) {
        return new FormError(stringRes, null);
    }

    /** Verbatim {@code message} from a backend {@code FieldViolation}. */
    @NonNull
    public static FormError of(@Nullable String message) {
        return new FormError(0, message == null ? "" : message);
    }

    @NonNull
    public String resolve(@NonNull Context context) {
        if (message != null && !message.trim().isEmpty()) {
            return message;
        }
        return stringRes == 0 ? "" : context.getString(stringRes);
    }

    public boolean isEmpty() {
        return message != null && message.trim().isEmpty() && stringRes == 0;
    }
}