package com.lpms.core.error;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * One entry of the {@code errors} array of the backend error envelope.
 * Only present for {@code VALIDATION_FAILED} (HTTP 400).
 *
 * <p>{@link #field} is a dotted/indexed path such as {@code items[0].quantity}
 * or {@code newPassword}; see {@link FieldPath} for matching helpers.</p>
 */
public final class FieldError {

    @SerializedName("field")
    private final String field;

    @SerializedName("message")
    private final String message;

    public FieldError(@Nullable String field, @Nullable String message) {
        this.field = field;
        this.message = message;
    }

    @Nullable
    public String getField() {
        return field;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    @NonNull
    @Override
    public String toString() {
        return (field == null ? "?" : field) + ": " + message;
    }
}