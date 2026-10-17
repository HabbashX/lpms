package com.lpms.data.repo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.core.network.NetworkCall;
import com.lpms.core.ui.UiState;
import com.lpms.core.util.AppPreferences;
import com.lpms.data.api.CategoryApi;
import com.lpms.data.dto.CategoryRequest;
import com.lpms.data.dto.CategoryResponse;
import com.lpms.local.LocalBackend;

import java.util.List;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Categories CRUD plus the in-memory cache the drug form dropdown and the drug-list
 * filter chip read from.
 *
 * <p>{@code GET /categories} returns a small, plain, non-paginated list, so it is loaded
 * once and reused; every write invalidates the cache so the next read re-fetches.</p>
 */
@Singleton
public final class CategoryRepository {

    private final CategoryApi categoryApi;
    private final LocalBackend localBackend;
    private final AppPreferences preferences;

    /** Guarded by {@code this}; null means "not loaded yet". */
    @Nullable
    private volatile List<CategoryResponse> cache;

    /** In-flight load, so several screens asking at once trigger one request. */
    @Nullable
    private volatile Single<List<CategoryResponse>> inFlight;

    @Inject
    public CategoryRepository(@NonNull CategoryApi categoryApi,
                              @NonNull LocalBackend localBackend,
                              @NonNull AppPreferences preferences) {
        this.categoryApi = categoryApi;
        this.localBackend = localBackend;
        this.preferences = preferences;
    }

    /** Cached list when available, otherwise a fresh fetch that primes the cache. */
    @NonNull
    public Single<List<CategoryResponse>> list() {
        // The cache is a network artifact. In offline mode the list is read straight from
        // SQLite each time, so a write is visible immediately rather than after an explicit
        // invalidation.
        if (preferences.isOffline()) {
            return localBackend.observeCategories().firstOrError();
        }
        List<CategoryResponse> cached = cache;
        if (cached != null) {
            return Single.just(cached);
        }
        synchronized (this) {
            if (cache != null) {
                return Single.just(cache);
            }
            Single<List<CategoryResponse>> pending = inFlight;
            if (pending != null) {
                return pending;
            }
            Single<List<CategoryResponse>> load = categoryApi.list()
                    .doOnSuccess(result -> {
                        synchronized (CategoryRepository.this) {
                            cache = result;
                            inFlight = null;
                        }
                    })
                    .doOnError(error -> {
                        synchronized (CategoryRepository.this) {
                            inFlight = null;
                        }
                    })
                    .onErrorResumeNext(error -> Single.error(
                            new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                    .subscribeOn(Schedulers.io())
                    .cache();
            inFlight = load;
            return load;
        }
    }

    /** Never-null view for the dropdown, mapping any failure to an empty list. */
    @NonNull
    public Single<List<CategoryResponse>> listOrEmpty() {
        return list().onErrorReturnItem(java.util.Collections.emptyList());
    }

    public boolean isCached() {
        return cache != null;
    }

    /** Forces the next {@link #list()} to hit the network. */
    public void invalidate() {
        synchronized (this) {
            cache = null;
            inFlight = null;
        }
    }

    @NonNull
    public Single<CategoryResponse> create(@NonNull String name) {
        if (preferences.isOffline()) {
            return localBackend.createCategory(name);
        }
        return categoryApi.create(new CategoryRequest(name))
                .doOnSuccess(this::onWrite)
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    @NonNull
    public Single<CategoryResponse> rename(long id, @NonNull String name) {
        if (preferences.isOffline()) {
            return localBackend.renameCategory(id, name);
        }
        return categoryApi.rename(id, new CategoryRequest(name))
                .doOnSuccess(this::onWrite)
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    /** 409 {@code CATEGORY_IN_USE} when any drug still references the category. */
    @NonNull
    public Completable delete(long id) {
        if (preferences.isOffline()) {
            return localBackend.deleteCategoryChecked(id);
        }
        return categoryApi.delete(id)
                .doOnComplete(this::invalidate)
                .onErrorResumeNext(error -> Completable.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    private void onWrite(@NonNull CategoryResponse ignored) {
        invalidate();
    }

    /** Convenience for ViewModels: content or error, empty list counts as empty. */
    @NonNull
    public Single<UiState<List<CategoryResponse>>> asUiState() {
        return list()
                .map(items -> items.isEmpty()
                        ? UiState.<List<CategoryResponse>>empty()
                        : UiState.content(items))
                .onErrorReturn(error -> UiState.error(NetworkCall.asApiError(error)));
    }

    /** Row identity for DiffUtil: categories are identified by id. */
    public static boolean sameCategory(@Nullable CategoryResponse a, @Nullable CategoryResponse b) {
        return Objects.equals(a == null ? null : a.getId(), b == null ? null : b.getId());
    }
}