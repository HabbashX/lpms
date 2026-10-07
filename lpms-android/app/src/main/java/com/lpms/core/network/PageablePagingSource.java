package com.lpms.core.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.paging.PagingSource;
import androidx.paging.PagingState;
import androidx.paging.rxjava3.RxPagingSource;
import com.lpms.core.error.ApiError;
import com.lpms.core.error.ApiErrorMapper;
import com.lpms.core.error.ApiException;
import com.lpms.data.dto.PageResponse;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import retrofit2.Call;

/**
 * Base class for every paged list in the app.
 *
 * <p>Maps the backend envelope ({@code content}, {@code page}, {@code totalPages}) onto
 * Paging 3 keys, and turns any failure into {@code LoadResult.Error} with a parsed
 * {@link ApiError} so list headers and snackbars can react to {@code code}.</p>
 *
 * <p>No {@code RemoteMediator}: the backend exposes no incremental-sync contract, so
 * pages are always fetched directly from the server.</p>
 *
 * @param <T> row type
 */
public abstract class PageablePagingSource<T> extends RxPagingSource<Integer, T>
        implements PagedSource<T> {

    /** Matches the backend default; the server clamps anything above 100. */
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private final ApiErrorMapper errorMapper;
    private final int pageSize;

    protected PageablePagingSource(@NonNull ApiErrorMapper errorMapper, int pageSize) {
        this.errorMapper = errorMapper;
        this.pageSize = pageSize > 0 ? Math.min(pageSize, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
    }

        @Override
    public int getPageSize() {
        return pageSize;
    }

    /**
     * Builds the call for a 0-based page. Subclasses bind whatever filters the screen
     * currently has.
     */
    @NonNull
    protected abstract Call<PageResponse<T>> callForPage(int page, int size);

    @NonNull
    @Override
    public Single<LoadResult<Integer, T>> loadSingle(@NonNull LoadParams<Integer> params) {
        final int page = params.getKey() == null ? 0 : params.getKey();
        final int size = pageSize;

        return NetworkCall.body(callForPage(page, size), errorMapper)
                .map(response -> toLoadResult(response, page))
                // Paging needs a Throwable; the ApiException carries the parsed ApiError
                // so load-state listeners can still branch on error.getError().getCode().
                .onErrorReturn(throwable -> new PagingSource.LoadResult.Error<Integer, T>(
                        throwable instanceof ApiException
                                ? throwable
                                : new ApiException(NetworkCall.asApiError(throwable))))
                .subscribeOn(Schedulers.io());
    }

    @NonNull
    private PagingSource.LoadResult<Integer, T> toLoadResult(@NonNull PageResponse<T> body, int page) {
        Integer prevKey = page <= 0 ? null : page - 1;
        Integer nextKey = page + 1 < body.getTotalPages() ? page + 1 : null;
        return new PagingSource.LoadResult.Page<>(body.getContent(), prevKey, nextKey);
    }

    /**
     * Standard "closest page wins" refresh key so re-querying the same filter keeps the
     * user's scroll position instead of jumping back to the top.
     *
     * <p>Java view of Paging's {@code Page}: the row list is {@code getData()} and the
     * page's starting offset inside the combined list is {@code getItemsBefore()}.</p>
     */
    @Nullable
    @Override
    public Integer getRefreshKey(@NonNull PagingState<Integer, T> state) {
        Integer anchor = state.getAnchorPosition();
        if (anchor == null) {
            return null;
        }

        PagingSource.LoadResult.Page<Integer, T> closest = null;
        int closestDistance = Integer.MAX_VALUE;

        for (PagingSource.LoadResult.Page<Integer, T> page : state.getPages()) {
            int offset = page.getItemsBefore();
            int size = page.getData().size();
            int distance;
            if (anchor < offset) {
                distance = offset - anchor;
            } else if (anchor >= offset + size) {
                distance = anchor - (offset + size - 1);
            } else {
                distance = 0;
            }
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = page;
            }
        }

        if (closest == null) {
            return null;
        }
        Integer prev = closest.getPrevKey();
        Integer next = closest.getNextKey();
        if (prev != null && anchor < closest.getItemsBefore() + closest.getData().size()) {
            return prev + 1;
        }
        return next != null ? next - 1 : null;
    }
}
