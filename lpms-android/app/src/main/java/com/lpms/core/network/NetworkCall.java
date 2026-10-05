package com.lpms.core.network;

import androidx.annotation.NonNull;

import com.lpms.core.error.ApiError;
import com.lpms.core.error.ApiErrorMapper;
import com.lpms.core.error.ApiException;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * The app's single async bridge between Retrofit and RxJava3.
 *
 * <p>Decision: <b>RxJava3 everywhere</b> ({@code RxJava3CallAdapterFactory}) so
 * repositories, ViewModels and the Paging 3 {@code RxPagingSource} all speak the
 * same types. Retrofit {@link Call} objects are still used for requests and for
 * paging, and are adapted here.</p>
 *
 * <p>Contract enforced by every method below:</p>
 * <ul>
 *   <li>Network I/O runs on the OkHttp dispatcher and is observed on
 *       {@code Schedulers.io()} — never the main thread.</li>
 *   <li>A non-2xx response never reaches {@code onSuccess}; it is mapped to
 *       {@link ApiException} carrying a fully parsed {@link ApiError}.</li>
 *   <li>204 endpoints surface as {@link Completable}.</li>
 *   <li>No automatic retry is ever attempted for a POST — the backend has no
 *       idempotency key, so a silent retry could create a duplicate sale.</li>
 * </ul>
 */
public final class NetworkCall {

    private NetworkCall() {
    }

    /** Adapts a call returning a body; {@code null} bodies become an error. */
    @NonNull
    public static <T> Single<T> body(@NonNull Call<T> call, @NonNull ApiErrorMapper mapper) {
        return Single.<T>create(emitter -> call.enqueue(new Callback<T>() {
            @Override
            public void onResponse(@NonNull Call<T> call, @NonNull Response<T> response) {
                if (!response.isSuccessful()) {
                    emitter.onError(new ApiException(mapper.map(response)));
                    return;
                }
                T value = response.body();
                if (value == null) {
                    emitter.onError(new ApiException(
                            ApiError.unparseable(response.code(), null)));
                    return;
                }
                emitter.onSuccess(value);
            }

            @Override
            public void onFailure(@NonNull Call<T> call, @NonNull Throwable t) {
                emitter.onError(new ApiException(mapper.map(t)));
            }
        })).observeOn(Schedulers.io());
    }

    /** For 204 No Content endpoints (logout, change-password, deactivate). */
    @NonNull
    public static Completable empty(@NonNull Call<ResponseBody> call,
                                    @NonNull ApiErrorMapper mapper) {
        return Completable.create(emitter -> call.enqueue(new Callback<ResponseBody>() {            @Override
            public void onResponse(@NonNull Call<ResponseBody> call,
                                   @NonNull Response<ResponseBody> response) {
                if (!response.isSuccessful()) {
                    emitter.onError(new ApiException(mapper.map(response)));
                    return;
                }
                emitter.onComplete();
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                emitter.onError(new ApiException(mapper.map(t)));
            }
        })).observeOn(Schedulers.io());
    }

    /** Unwraps {@link ApiException} so callers never branch on exception types. */
    @NonNull
    public static ApiError asApiError(@NonNull Throwable throwable) {
        if (throwable instanceof ApiException) {
            return ((ApiException) throwable).getError();
        }
        return ApiError.network(
                throwable.getMessage() == null ? "Unexpected error" : throwable.getMessage());
    }
}