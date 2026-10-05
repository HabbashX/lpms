package com.lpms.core.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.core.error.ApiError;

/**
 * Per-screen state wrapper. Every ViewModel exposes exactly one of these.
 *
 * <p>States are terminal for a single emission: LOADING then exactly one of
 * CONTENT / EMPTY / ERROR. Retrying re-emits LOADING.</p>
 */
public abstract class UiState<T> {

    private UiState() {
    }

    public static <T> UiState<T> loading() {
        return new Loading<>();
    }

    public static <T> UiState<T> content(@NonNull T value) {
        return new Content<>(value);
    }

    /** Content is loaded but has no rows: distinct from an error and from content. */
    public static <T> UiState<T> empty() {
        return new Empty<>();
    }

    public static <T> UiState<T> error(@NonNull ApiError error) {
        return new Error<>(error);
    }

    public final boolean isLoading() {
        return this instanceof Loading;
    }

    public final boolean isContent() {
        return this instanceof Content;
    }

    public final boolean isEmpty() {
        return this instanceof Empty;
    }

    public final boolean isError() {
        return this instanceof Error;
    }

    /** @return payload when CONTENT, otherwise null. */
    @Nullable
    public final T valueOrNull() {
        return this instanceof Content ? ((Content<T>) this).value : null;
    }

    /** @return error when ERROR, otherwise null. */
    @Nullable
    public final ApiError errorOrNull() {
        return this instanceof Error ? ((Error<T>) this).error : null;
    }

    public static final class Loading<T> extends UiState<T> {
        private Loading() {
        }
    }

    public static final class Content<T> extends UiState<T> {
        private final T value;

        private Content(T value) {
            this.value = value;
        }

        @NonNull
        public T value() {
            return value;
        }
    }

    public static final class Empty<T> extends UiState<T> {
        private Empty() {
        }
    }

    public static final class Error<T> extends UiState<T> {
        private final ApiError error;

        private Error(ApiError error) {
            this.error = error;
        }

        @NonNull
        public ApiError error() {
            return error;
        }
    }
}