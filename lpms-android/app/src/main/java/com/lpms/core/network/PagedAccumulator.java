package com.lpms.core.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.paging.PagingSource;
import com.lpms.core.error.ApiError;
import com.lpms.core.error.ApiErrorCodes;
import com.lpms.core.error.ApiException;
import com.lpms.data.dto.PageResponse;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Accumulates pages from a {@link PageablePagingSource} into one growing list.
 *
 * <p>Small, explicit paging engine used by the list screens. It exists because the
 * Java/RxJava3 combination has no usable {@code Pager}: {@code paging-rxjava3} ships
 * only {@code RxPagingSource} and {@code androidx.paging.Pager} is coroutine-first. This
 * class keeps the app on RxJava3 and still uses a real Paging 3
 * {@code LoadResult.Page} with prev/next keys.</p>
 *
 * <p>Thread-safe on the caller's terms: every mutation happens on the main thread
 * because the ViewModel observes upstream on the main thread.</p>
 */
public final class PagedAccumulator<T> {

    private final PageablePagingSource<T> source;

    private final List<T> items = new ArrayList<>();
    private Integer nextKey = 0;
    private boolean endReached;

    public PagedAccumulator(@NonNull PageablePagingSource<T> source) {
        this.source = source;
    }

    public int size() {
        return items.size();
    }

    public boolean endReached() {
        return endReached;
    }

    /** True when there is another page to request. */
    public boolean canLoadMore() {
        return !endReached && nextKey != null;
    }

    @NonNull
    public List<T> items() {
        return new ArrayList<>(items);
    }

    /** Drops everything and (re)loads the first page. */
    @NonNull
    public Single<Outcome<T>> refresh() {
        return load(null, true);
    }

    /** Loads the next page; a no-op when the end is already reached. */
    @NonNull
    public Single<Outcome<T>> loadNext() {
        if (!canLoadMore()) {
            return Single.just(new Outcome<T>(items(), true, null));
        }
        return load(nextKey, false);
    }

    @NonNull
    private Single<Outcome<T>> load(@Nullable Integer key, boolean first) {
        int loadSize = source.getPageSize();
        PagingSource.LoadParams<Integer> params = first
                ? new PagingSource.LoadParams.Refresh<>(key == null ? 0 : key, loadSize, false)
                : new PagingSource.LoadParams.Append<>(key == null ? 0 : key, loadSize, false);

        return source.loadSingle(params)
                .observeOn(Schedulers.io())
                .map(result -> onResult(result, first))
                .onErrorReturn(throwable -> new Outcome<>(
                        items(),
                        false,
                        throwable instanceof ApiException
                                ? ((ApiException) throwable).getError()
                                : ApiError.network(String.valueOf(throwable.getMessage()))));
    }

    @NonNull
    private Outcome<T> onResult(@NonNull PagingSource.LoadResult<Integer, T> result, boolean first) {
        if (result instanceof PagingSource.LoadResult.Page) {
            @SuppressWarnings("unchecked")
            PagingSource.LoadResult.Page<Integer, T> page =
                    (PagingSource.LoadResult.Page<Integer, T>) result;
            List<T> pageItems = page.getData();
            if (first) {
                items.clear();
            }
            items.addAll(pageItems);
            nextKey = page.getNextKey();
            endReached = nextKey == null;
            return new Outcome<>(items(), endReached, null);
        }
        if (result instanceof PagingSource.LoadResult.Error) {
            @SuppressWarnings("unchecked")
            PagingSource.LoadResult.Error<Integer, T> error =
                    (PagingSource.LoadResult.Error<Integer, T>) result;
            Throwable cause = error.getThrowable();
            return new Outcome<>(items(), endReached,
                    cause instanceof ApiException
                            ? ((ApiException) cause).getError()
                            : ApiError.network(String.valueOf(cause.getMessage())));
        }
        // Invalid: the source was invalidated (filter change); caller restarts.
        endReached = true;
        return new Outcome<>(items(), true, null);
    }

    /** Result of one page request. */
    public static final class Outcome<T> {

        private final List<T> items;
        private final boolean endReached;
        private final ApiError error;

        Outcome(@NonNull List<T> items, boolean endReached, @Nullable ApiError error) {
            this.items = items;
            this.endReached = endReached;
            this.error = error;
        }

        @NonNull
        public List<T> getItems() {
            return items;
        }

        public boolean isEndReached() {
            return endReached;
        }

        @Nullable
        public ApiError getError() {
            return error;
        }
    }
}