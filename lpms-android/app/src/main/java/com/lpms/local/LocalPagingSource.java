package com.lpms.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingSource;
import androidx.paging.PagingState;

import com.lpms.core.error.ApiErrorMapper;
import com.lpms.core.network.PageablePagingSource;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Paginates an in-memory list, so offline lists behave like online ones.
 *
 * <p>It extends {@link PageablePagingSource} rather than replacing it because the
 * repositories return that type and {@code InventoryViewModel} adapts it directly. The
 * local source therefore overrides {@link #loadSingle} and never calls
 * {@link #callForPage}: there is no request to build, and the whole list is already in
 * hand. {@code callForPage} is left to throw rather than return null, so if a future
 * change routes a local source back through the network path the failure is loud.</p>
 *
 * @param <T> row type
 */
public final class LocalPagingSource<T> extends PageablePagingSource<T> {

    private final Single<List<T>> rows;

    /**
     * @param rows      every row, in final display order; paged here rather than in SQL
     *                  because offline lists are small and ordering has already been
     *                  decided by the local backend
     * @param errorMapper kept only for the inherited shape; no call is mapped through it
     */
    public LocalPagingSource(@NonNull Single<List<T>> rows, @Nullable ApiErrorMapper errorMapper) {
        super(errorMapper, DEFAULT_PAGE_SIZE);
        this.rows = rows;
    }

    @NonNull
    @Override
    public Single<LoadResult<Integer, T>> loadSingle(@NonNull LoadParams<Integer> params) {
        final int page = params.getKey() == null ? 0 : params.getKey();
        final int size = getPageSize();

        return rows
                .map(all -> {
                    int from = Math.min(page * size, all.size());
                    int to = Math.min(from + size, all.size());
                    List<T> slice = new ArrayList<>(all.subList(from, to));
                    Integer prevKey = page <= 0 ? null : page - 1;
                    Integer nextKey = to < all.size() ? page + 1 : null;
                    return (LoadResult<Integer, T>)
                            new LoadResult.Page<>(slice, prevKey, nextKey);
                })
                .subscribeOn(Schedulers.io());
    }

    @NonNull
    @Override
    protected retrofit2.Call<com.lpms.data.dto.PageResponse<T>> callForPage(int page, int size) {
        throw new UnsupportedOperationException(
                "LocalPagingSource reads from the local database and never calls the network");
    }

    @Nullable
    @Override
    public Integer getRefreshKey(@NonNull PagingState<Integer, T> state) {
        // Same "closest page wins" behaviour as the network source, so re-filtering keeps
        // the scroll position instead of jumping to the top.
        Integer anchor = state.getAnchorPosition();
        if (anchor == null) {
            return null;
        }
        LoadResult.Page<Integer, T> closest = null;
        int closestDistance = Integer.MAX_VALUE;
        for (LoadResult.Page<Integer, T> page : state.getPages()) {
            int offset = page.getItemsBefore();
            int count = page.getData().size();
            int distance;
            if (anchor < offset) {
                distance = offset - anchor;
            } else if (anchor >= offset + count) {
                distance = anchor - (offset + count - 1);
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
