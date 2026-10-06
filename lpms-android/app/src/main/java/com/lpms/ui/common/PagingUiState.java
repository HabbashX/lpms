package com.lpms.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.core.error.ApiError;

/**
 * Paging state for a list screen.
 *
 * <p>{@code PagingDataAdapter} is not reachable from Java with this dependency set
 * ({@code paging-rxjava3} ships only {@code RxPagingSource}, and
 * {@code androidx.paging.Pager} is coroutine-only), so the app drives paging itself on
 * top of {@code RxPagingSource} and publishes this state instead.</p>
 */
public final class PagingUiState {

    public enum Stage {
        /** First page loading, nothing on screen yet. */
        INITIAL,
        /** More rows being fetched while existing rows stay visible. */
        APPENDING,
        /** Fully loaded; no further request until the list is scrolled again. */
        IDLE,
        /** The request failed. {@link #error} says whether retry makes sense. */
        ERROR,
        /** Server returned an empty first page. */
        EMPTY
    }

    private final Stage stage;
    private final ApiError error;
    private final boolean endReached;

    private PagingUiState(@NonNull Stage stage, @Nullable ApiError error, boolean endReached) {
        this.stage = stage;
        this.error = error;
        this.endReached = endReached;
    }

    @NonNull
    public static PagingUiState initial() {
        return new PagingUiState(Stage.INITIAL, null, false);
    }

    @NonNull
    public static PagingUiState idle(boolean endReached) {
        return new PagingUiState(Stage.IDLE, null, endReached);
    }

    @NonNull
    public static PagingUiState appending() {
        return new PagingUiState(Stage.APPENDING, null, false);
    }

    @NonNull
    public static PagingUiState empty() {
        return new PagingUiState(Stage.EMPTY, null, true);
    }

    @NonNull
    public static PagingUiState error(@NonNull ApiError error) {
        return new PagingUiState(Stage.ERROR, error, false);
    }

    @NonNull
    public Stage getStage() {
        return stage;
    }

    @Nullable
    public ApiError getError() {
        return error;
    }

    /** True when the server reported no more pages. */
    public boolean isEndReached() {
        return endReached;
    }

    public boolean isInitialLoading() {
        return stage == Stage.INITIAL;
    }

    /** True while a page is in flight but rows are already on screen. */
    public boolean isAppending() {
        return stage == Stage.APPENDING;
    }

    public boolean isEmpty() {
        return stage == Stage.EMPTY;
    }

    public boolean isError() {
        return stage == Stage.ERROR;
    }
}