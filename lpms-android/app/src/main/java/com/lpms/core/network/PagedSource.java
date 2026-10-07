package com.lpms.core.network;

import androidx.annotation.NonNull;
import androidx.paging.PagingSource;

import io.reactivex.rxjava3.core.Single;

/**
 * The slice of a Paging 3 source that {@link PagedAccumulator} actually needs.
 *
 * <p>Declaring it lets the accumulator accept any source, not just a
 * {@link PageablePagingSource} - notably {@link MappedPagingSource}, which adapts an
 * existing source to a different row type without another round trip.</p>
 *
 * @param <T> row type
 */
public interface PagedSource<T> {

    /** Rows per page, used to build the Paging load params. */
    int getPageSize();

    @NonNull
    Single<PagingSource.LoadResult<Integer, T>> loadSingle(
            @NonNull PagingSource.LoadParams<Integer> params);
}