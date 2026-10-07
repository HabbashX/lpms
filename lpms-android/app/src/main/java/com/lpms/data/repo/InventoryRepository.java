package com.lpms.data.repo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.core.network.NetworkCall;
import com.lpms.core.network.PageablePagingSource;
import com.lpms.core.error.ApiErrorMapper;
import com.lpms.data.api.InventoryApi;
import com.lpms.data.dto.CreatePurchaseRequest;
import com.lpms.data.dto.DrugPricingResponse;
import com.lpms.data.dto.DrugValuationResponse;
import com.lpms.data.dto.ExpiringBatchResponse;
import com.lpms.data.dto.LowStockDrugResponse;
import com.lpms.data.dto.PageResponse;
import com.lpms.data.dto.StockBatchResponse;

import java.math.BigDecimal;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import retrofit2.Call;

/**
 * Stock batches, valuation, low stock, expiring batches and drug pricing.
 *
 * <p>FEFO consumption and weighted-average costing are computed server-side; this class
 * only carries the results.</p>
 *
 * <p><b>Pricing rule:</b> {@link #pricing} takes <b>at most one</b> of
 * {@code profitPerUnit} / {@code marginPercent}. Sending both is rejected with 400
 * {@code INVALID_PRICING}, and a margin outside 0 ≤ m &lt; 100 with 400
 * {@code INVALID_MARGIN}.</p>
 */
@Singleton
public final class InventoryRepository {

    private final InventoryApi inventoryApi;

    @Inject
    public InventoryRepository(@NonNull InventoryApi inventoryApi) {
        this.inventoryApi = inventoryApi;
    }

    /** Which what-if input the user is driving. */
    public enum PricingMode {
        /** No query parameter: the response only reports the current position. */
        NONE,
        /** {@code profitPerUnit=X} → suggested = cost + X. */
        PROFIT_PER_UNIT,
        /** {@code marginPercent=M} → suggested = cost ÷ (1 − M/100). */
        MARGIN_PERCENT
    }

    /**
     * ADMIN/PHARMACIST only — the one endpoint that exposes cost.
     *
     * @param mode          which single what-if parameter to send
     * @param profitPerUnit ignored unless {@code mode == PROFIT_PER_UNIT}
     * @param marginPercent ignored unless {@code mode == MARGIN_PERCENT}
     */
    @NonNull
    public Single<DrugPricingResponse> pricing(long drugId,
                                              @NonNull PricingMode mode,
                                              @Nullable BigDecimal profitPerUnit,
                                              @Nullable BigDecimal marginPercent) {
        String profit = mode == PricingMode.PROFIT_PER_UNIT
                ? com.lpms.core.util.Money.toPlainString(profitPerUnit) : null;
        String margin = mode == PricingMode.MARGIN_PERCENT
                ? com.lpms.core.util.Money.toPlainString(marginPercent) : null;
        return inventoryApi.pricing(drugId, profit, margin)
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    @NonNull
    public Single<DrugPricingResponse> pricing(long drugId) {
        return pricing(drugId, PricingMode.NONE, null, null);
    }

    @NonNull
    public Single<DrugValuationResponse> valuation(long drugId) {
        return inventoryApi.valuationForDrug(drugId)
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    /** Plain list, FEFO order, not paginated. */
    @NonNull
    public Single<List<StockBatchResponse>> batchesForDrug(long drugId) {
        return inventoryApi.batchesForDrug(drugId)
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    @NonNull
    public PageablePagingSource<StockBatchResponse> batches(@NonNull Filter filter,
                                                             @NonNull ApiErrorMapper mapper) {
        return new BatchPagingSource(filter, mapper);
    }

    @NonNull
    public PageablePagingSource<LowStockDrugResponse> lowStock(
            @Nullable String search, @NonNull ApiErrorMapper mapper) {
        return new LowStockPagingSource(search, mapper);
    }

    @NonNull
    public PageablePagingSource<ExpiringBatchResponse> expiring(
            @Nullable Integer days, @Nullable Boolean expired, @Nullable String search,
            @NonNull ApiErrorMapper mapper) {
        return new ExpiringPagingSource(days, expired, search, mapper);
    }

    /** ADMIN/PHARMACIST. Creates an immutable batch. */
    @NonNull
    public Single<StockBatchResponse> purchase(@NonNull CreatePurchaseRequest request) {
        return inventoryApi.purchase(request)
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    /** Query state for {@code GET /inventory/batches}. */
    public static final class Filter {

        private final Long drugId;
        private final String supplier;
        private final String search;
        private final Boolean expired;
        private final Integer expiringDays;
        private final String sort;

        public Filter(@Nullable Long drugId, @Nullable String supplier, @Nullable String search,
                      @Nullable Boolean expired, @Nullable Integer expiringDays,
                      @Nullable String sort) {
            this.drugId = drugId;
            this.supplier = supplier;
            this.search = search;
            this.expired = expired;
            this.expiringDays = expiringDays;
            this.sort = sort;
        }

        @NonNull
        public static Filter none() {
            return new Filter(null, null, null, null, null, "receivedAt,desc");
        }

        /**
         * Batches matching a free-text search, newest received first.
         *
         * <p>{@code search} matches the drug name or the batch number; the expiry filters
         * stay null so this is not narrowed to expired or expiring stock.</p>
         */
        @NonNull
        public static Filter batches(@Nullable String search) {
            return new Filter(null, null, search, null, null, "receivedAt,desc");
        }

        @Nullable
        public Long getDrugId() {
            return drugId;
        }

        @Nullable
        public String getSupplier() {
            return supplier;
        }

        @Nullable
        public String getSearch() {
            return search;
        }

        @Nullable
        public Boolean getExpired() {
            return expired;
        }

        @Nullable
        public Integer getExpiringDays() {
            return expiringDays;
        }

        @Nullable
        public String getSort() {
            return sort;
        }
    }

    private final class BatchPagingSource extends PageablePagingSource<StockBatchResponse> {

        private final Filter filter;

        BatchPagingSource(@NonNull Filter filter, @NonNull ApiErrorMapper mapper) {
            super(mapper, DEFAULT_PAGE_SIZE);
            this.filter = filter;
        }

        @NonNull
        @Override
        protected Call<PageResponse<StockBatchResponse>> callForPage(int page, int size) {
            return inventoryApi.batches(filter.getDrugId(), filter.getSupplier(),
                    filter.getSearch(), filter.getExpired(), filter.getExpiringDays(),
                    page, size, filter.getSort());
        }
    }

    private final class LowStockPagingSource extends PageablePagingSource<LowStockDrugResponse> {

        private final String search;

        LowStockPagingSource(@Nullable String search, @NonNull ApiErrorMapper mapper) {
            super(mapper, DEFAULT_PAGE_SIZE);
            this.search = search;
        }

        @NonNull
        @Override
        protected Call<PageResponse<LowStockDrugResponse>> callForPage(int page, int size) {
            return inventoryApi.lowStock(search, page, size);
        }
    }

    private final class ExpiringPagingSource extends PageablePagingSource<ExpiringBatchResponse> {

        private final Integer days;
        private final Boolean expired;
        private final String search;

        ExpiringPagingSource(@Nullable Integer days, @Nullable Boolean expired,
                             @Nullable String search, @NonNull ApiErrorMapper mapper) {
            super(mapper, DEFAULT_PAGE_SIZE);
            this.days = days;
            this.expired = expired;
            this.search = search;
        }

        @NonNull
        @Override
        protected Call<PageResponse<ExpiringBatchResponse>> callForPage(int page, int size) {
            return inventoryApi.expiring(days, expired, search, page, size);
        }
    }

    /**
     * Whole-pharmacy stock valuation, one row per drug.
     *
     * <p>Distinct from {@link #valuation(long)}, which resolves a single drug: this pages
     * the {@code GET /inventory/valuation} report so the pharmacy can see the total value of
     * everything it holds.</p>
     */
    @NonNull
    public PageablePagingSource<DrugValuationResponse> valuationPaging(@Nullable String search,
                                                                       @Nullable String sort,
                                                                       @NonNull
                                                                       ApiErrorMapper errorMapper) {
        return new ValuationPagingSource(search, sort, errorMapper);
    }

    private final class ValuationPagingSource
            extends PageablePagingSource<DrugValuationResponse> {

        private final String search;
        private final String sort;

        ValuationPagingSource(@Nullable String search, @Nullable String sort,
                              @NonNull ApiErrorMapper mapper) {
            super(mapper, DEFAULT_PAGE_SIZE);
            this.search = search;
            this.sort = sort;
        }

        @NonNull
        @Override
        protected Call<PageResponse<DrugValuationResponse>> callForPage(int page, int size) {
            return inventoryApi.valuation(search, page, size, sort);
        }
    }
}