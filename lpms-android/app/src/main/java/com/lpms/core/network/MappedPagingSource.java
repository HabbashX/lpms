package com.lpms.core.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingSource;
import androidx.paging.PagingState;
import androidx.paging.rxjava3.RxPagingSource;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Function;

/**
 * Adapts an existing Paging source to a different row type.
 *
 * <p>Used where one screen hosts several endpoints that return different payloads - the
 * inventory screen shows batches, low-stock drugs, expiring batches and valuation rows in
 * one list. Mapping here rather than in the repository keeps the repositories a plain
 * mirror of the API, and mapping at the load boundary means the transformation happens
 * once per row instead of once per render.</p>
 *
 * <p>An invalid {@code LoadResult} is passed through untouched: Paging uses it to signal
 * that the source was superseded, and translating it would break that contract.</p>
 *
 * @param <S> source row type
 * @param <T> row type the screen renders
 */
public final class MappedPagingSource<S, T> extends RxPagingSource<Integer, T>
        implements PagedSource<T> {

    private final RxPagingSource<Integer, S> delegate;
    private final Function<S, T> mapper;
    private final int pageSize;

    public MappedPagingSource(@NonNull RxPagingSource<Integer, S> delegate,
                              @NonNull Function<S, T> mapper,
                              int pageSize) {
        this.delegate = delegate;
        this.mapper = mapper;
        this.pageSize = pageSize > 0 ? pageSize : PageablePagingSource.DEFAULT_PAGE_SIZE;
    }

    @Override
    public int getPageSize() {
        return pageSize;
    }

    @NonNull
    @Override
    public Single<PagingSource.LoadResult<Integer, T>> loadSingle(
            @NonNull PagingSource.LoadParams<Integer> params) {
        return delegate.loadSingle(params).map(result -> mapResult(result));
    }

    @NonNull
    private PagingSource.LoadResult<Integer, T> mapResult(
            @NonNull PagingSource.LoadResult<Integer, S> result) {
        if (!(result instanceof PagingSource.LoadResult.Page)) {
            @SuppressWarnings("unchecked")
            PagingSource.LoadResult<Integer, T> untouched =
                    (PagingSource.LoadResult<Integer, T>) (PagingSource.LoadResult<?, ?>) result;
            return untouched;
        }
        @SuppressWarnings("unchecked")
        PagingSource.LoadResult.Page<Integer, S> page =
                (PagingSource.LoadResult.Page<Integer, S>) result;

        List<T> mapped = new ArrayList<>(page.getData().size());
        for (S row : page.getData()) {
            try {
                mapped.add(mapper.apply(row));
            } catch (Throwable failure) {
                // RxJava's Function.apply declares Throwable; a row that cannot be mapped
                // must not take the whole page down.
                throw new IllegalStateException("could not map inventory row", failure);
            }
        }
        return new PagingSource.LoadResult.Page<>(mapped, page.getPrevKey(), page.getNextKey());
    }

    /**
     * Keeps the "closest page wins" refresh key. Delegating to the wrapped source would
     * return keys for {@code S}; this reproduces the same policy over {@code T} so a
     * refresh still keeps the user's scroll position.
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
