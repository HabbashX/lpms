package com.lpms.data.repo;

import androidx.annotation.NonNull;

import com.lpms.core.error.ApiError;
import com.lpms.core.error.ApiErrorMapper;
import com.lpms.core.network.NetworkCall;
import com.lpms.data.dto.PageResponse;

import io.reactivex.rxjava3.core.Single;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Bridges a Retrofit {@link Call} to a Paging 3 {@code RxPagingSource}: it loads one
 * page and converts both failure modes into a {@link LoadResult.Error} carrying a
 * fully parsed {@link ApiError}.
 *
 * <p>No {@code RemoteMediator} is used: the backend offers no cache-control or
 * incremental-sync endpoints, so each load is a plain server page.</p>
 */
public final class PageLoader {

    private PageLoader() {
    }

    /**
     * @param call a call already bound to a concrete 0-based page and size
     * @return the page body, or an error carrying the mapped {@code ApiError}
     */
    @NonNull
    public static <T> Single<T> load(@NonNull Call<T> call, @NonNull ApiErrorMapper mapper) {
        return NetworkCall.body(call, mapper);
    }

    /** Adapts a raw Retrofit response page for sources that already have the body. */
    @NonNull
    public static <T> Single<PageResponse<T>> unwrap(@NonNull Response<PageResponse<T>> response,
                                                      @NonNull ApiErrorMapper mapper) {
        if (!response.isSuccessful()) {
            return Single.error(new com.lpms.core.error.ApiException(mapper.map(response)));
        }
        PageResponse<T> body = response.body();
        if (body == null) {
            return Single.error(new com.lpms.core.error.ApiException(
                    ApiError.unparseable(response.code(), null)));
        }
        return Single.just(body);
    }
}