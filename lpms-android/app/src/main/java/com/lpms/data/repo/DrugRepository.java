package com.lpms.data.repo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.core.network.NetworkCall;
import com.lpms.core.network.PageablePagingSource;
import com.lpms.core.error.ApiErrorMapper;
import com.lpms.data.api.DrugApi;
import com.lpms.data.dto.CreateDrugRequest;
import com.lpms.data.dto.DrugResponse;
import com.lpms.data.dto.PageResponse;
import com.lpms.data.dto.UpdateDrugRequest;

import java.util.List;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import retrofit2.Call;

/**
 * Drugs CRUD + filtered paging.
 *
 * <p>The filter set is immutable ({@link Filter}) and value-compared, so the UI can hand
 * a whole new snapshot to {@link #paging(Filter)} and Paging starts a fresh generation
 * without any manual invalidation dance.</p>
 */
@Singleton
public final class DrugRepository {

    /** POS search returns one short page; the list screen pages normally. */
    private static final int SEARCH_PAGE_SIZE = 20;

    private final DrugApi drugApi;

    @Inject
    public DrugRepository(@NonNull DrugApi drugApi) {
        this.drugApi = drugApi;
    }

    /** Paging source for one immutable filter snapshot. */
    @NonNull
    public PageablePagingSource<DrugResponse> paging(@NonNull Filter filter,
                                                     @NonNull ApiErrorMapper errorMapper) {
        return new DrugPagingSource(filter, errorMapper);
    }

    /** POS scanner lookup: 404 {@code NOT_FOUND} when the barcode is unknown. */
    @NonNull
    public Single<DrugResponse> byBarcode(@NonNull String barcode) {
        return drugApi.byBarcode(barcode)
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    /**
     * POS type-ahead search: first page of {@code GET /drugs} matching name +
     * genericName, active drugs only, sorted by name.
     */
    @NonNull
    public Single<List<DrugResponse>> byNameSearch(@NonNull String query) {
        Single<List<DrugResponse>> call = NetworkCall.body(
                        drugApi.list(query, null, null, Boolean.TRUE, null, null, 0,
                                SEARCH_PAGE_SIZE, Filter.SORT_NAME_ASC),
                        new com.lpms.core.error.ApiErrorMapper(
                                com.lpms.core.network.json.LpmsGson.get()))
                .map(page -> page.getContent());
        return call.subscribeOn(Schedulers.io());
    }

    @NonNull
    public Single<DrugResponse> get(long id) {
        return drugApi.get(id)
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    @NonNull
    public Single<DrugResponse> create(@NonNull CreateDrugRequest request) {
        return drugApi.create(request)
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    @NonNull
    public Single<DrugResponse> update(long id, @NonNull UpdateDrugRequest request) {
        return drugApi.update(id, request)
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    /** Soft delete: the drug becomes inactive and keeps its history. */
    @NonNull
    public Completable deactivate(long id) {
        return drugApi.deactivate(id)
                .onErrorResumeNext(error -> Completable.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    /**
     * Immutable query state for {@code GET /drugs}.
     *
     * <p>{@code search} matches name + genericName. Only the sort properties the backend
     * documents are exposed — unknown ones are silently ignored server-side.</p>
     */
    public static final class Filter {

        public static final String SORT_NAME_ASC = "name,asc";
        public static final String SORT_NAME_DESC = "name,desc";
        public static final String SORT_PRICE_ASC = "sellingPrice,asc";
        public static final String SORT_PRICE_DESC = "sellingPrice,desc";
        public static final String SORT_QUANTITY_ASC = "currentQuantity,asc";
        public static final String SORT_QUANTITY_DESC = "currentQuantity,desc";
        public static final String SORT_NEWEST = "createdAt,desc";

        private final String search;
        private final Long categoryId;
        private final String dosageForm;
        private final Boolean active;
        private final Boolean lowStock;
        private final String sort;

        public Filter(@Nullable String search,
                      @Nullable Long categoryId,
                      @Nullable String dosageForm,
                      @Nullable Boolean active,
                      @Nullable Boolean lowStock,
                      @Nullable String sort) {
            this.search = search;
            this.categoryId = categoryId;
            this.dosageForm = dosageForm;
            this.active = active;
            this.lowStock = lowStock;
            this.sort = sort;
        }

        /** No filters, server default sort ({@code name,asc}). */
        @NonNull
        public static Filter none() {
            return new Filter(null, null, null, null, null, null);
        }

        @Nullable
        public String getSearch() {
            return search;
        }

        @Nullable
        public Long getCategoryId() {
            return categoryId;
        }

        @Nullable
        public String getDosageForm() {
            return dosageForm;
        }

        @Nullable
        public Boolean getActive() {
            return active;
        }

        @Nullable
        public Boolean getLowStock() {
            return lowStock;
        }

        @Nullable
        public String getSort() {
            return sort;
        }

        public boolean hasAnyFilter() {
            return (search != null && !search.trim().isEmpty())
                    || categoryId != null
                    || dosageForm != null
                    || active != null
                    || lowStock != null;
        }

        public int activeFilterCount() {
            int count = 0;
            if (categoryId != null) {
                count++;
            }
            if (dosageForm != null) {
                count++;
            }
            if (active != null) {
                count++;
            }
            if (lowStock != null) {
                count++;
            }
            return count;
        }

        @NonNull
        public Filter withSearch(@Nullable String value) {
            return new Filter(value, categoryId, dosageForm, active, lowStock, sort);
        }

        @NonNull
        public Filter withCategory(@Nullable Long value) {
            return new Filter(search, value, dosageForm, active, lowStock, sort);
        }

        @NonNull
        public Filter withDosageForm(@Nullable String value) {
            return new Filter(search, categoryId, value, active, lowStock, sort);
        }

        @NonNull
        public Filter withActive(@Nullable Boolean value) {
            return new Filter(search, categoryId, dosageForm, value, lowStock, sort);
        }

        @NonNull
        public Filter withLowStock(@Nullable Boolean value) {
            return new Filter(search, categoryId, dosageForm, active, value, sort);
        }

        @NonNull
        public Filter withSort(@Nullable String value) {
            return new Filter(search, categoryId, dosageForm, active, lowStock, value);
        }

        /** Keeps the sort, clears every filter. */
        @NonNull
        public Filter cleared() {
            return new Filter(null, null, null, null, null, sort);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Filter)) {
                return false;
            }
            Filter other = (Filter) o;
            return Objects.equals(search, other.search)
                    && Objects.equals(categoryId, other.categoryId)
                    && Objects.equals(dosageForm, other.dosageForm)
                    && Objects.equals(active, other.active)
                    && Objects.equals(lowStock, other.lowStock)
                    && Objects.equals(sort, other.sort);
        }

        @Override
        public int hashCode() {
            return Objects.hash(search, categoryId, dosageForm, active, lowStock, sort);
        }

        @NonNull
        @Override
        public String toString() {
            // Never log free-text search terms from a customer-facing field; keep it terse.
            return "Filter{category=" + categoryId + ", form=" + dosageForm
                    + ", active=" + active + ", lowStock=" + lowStock
                    + ", sort=" + sort + ", search=" + (search == null ? "no" : "yes") + "}";
        }
    }

    /** One page of drugs for a fixed filter snapshot. */
    private final class DrugPagingSource extends PageablePagingSource<DrugResponse> {

        private final Filter filter;

        DrugPagingSource(@NonNull Filter filter, @NonNull ApiErrorMapper errorMapper) {
            super(errorMapper, DEFAULT_PAGE_SIZE);
            this.filter = filter;
        }

        @NonNull
        @Override
        protected Call<PageResponse<DrugResponse>> callForPage(int page, int size) {
            return drugApi.list(
                    filter.getSearch(),
                    filter.getCategoryId(),
                    filter.getDosageForm(),
                    filter.getActive(),
                    filter.getLowStock(),
                    null,
                    page,
                    size,
                    filter.getSort());
        }
    }
}