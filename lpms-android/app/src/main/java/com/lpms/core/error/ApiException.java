package com.lpms.core.error;

import androidx.annotation.NonNull;

/** Carries an {@link ApiError} through RxJava3 error channels. */
public final class ApiException extends RuntimeException {

    private final transient ApiError error;

    public ApiException(@NonNull ApiError error) {
        super(error.getCode() + ": " + error.getMessage());
        this.error = error;
    }

    @NonNull
    public ApiError getError() {
        return error;
    }
}