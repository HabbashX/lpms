package com.lpms.ui.inventory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.R;
import com.lpms.core.error.ApiErrorMapper;
import com.lpms.core.network.MappedPagingSource;
import com.lpms.core.network.NetworkCall;
import com.lpms.core.network.PagedAccumulator;
import com.lpms.core.network.PagedSource;
import com.lpms.core.network.PageablePagingSource;
import com.lpms.data.dto.DrugValuationResponse;
import com.lpms.data.dto.ExpiringBatchResponse;
import com.lpms.data.dto.LowStockDrugResponse;
import com.lpms.data.dto.StockBatchResponse;
import com.lpms.data.repo.InventoryRepository;
import com.lpms.ui.common.PagingUiState;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * Inventory: batches, low stock, expiring batches and valuation.
 *
 * <p>One screen with four modes, because all four answer the same question and a
 * pharmacist cross-checks them while ordering. Switching mode rebuilds the paging source
 * and restarts at page 0; the rows are mapped to {@link InventoryRow} so the adapter can
 * stay mode-agnostic.</p>
 *
 * <p>The low-stock report has no filters beyond search, and expiring batches additionally
 * take a horizon in days - both are honoured by the repository rather than re-implemented
 * here.</p>
 */
@HiltViewModel
public final class InventoryViewModel extends ViewModel {

    /** Default horizon for the expiring view: the next quarter. */
    private static final int DEFAULT_EXPIRING_DAYS = 90;

    /** Matches {@link StockBatchResponse.ExpirationSeverity}. */
    private static final int CRITICAL_DAYS = 30;

    private final InventoryRepository inventoryRepository;
    private final ApiErrorMapper errorMapper;
    private final CompositeDisposable disposables = new CompositeDisposable();

    /** Which of the four views is showing. */
    public enum Mode {
        BATCHES(R.string.inventory_tab_batches, R.string.inventory_empty_batches),
        LOW_STOCK(R.string.nav_low_stock, R.string.inventory_empty_low_stock),
        EXPIRING(R.string.inventory_tab_expiring, R.string.inventory_empty_expiring),
        VALUATION(R.string.inventory_tab_valuation, R.string.inventory_empty_valuation);

        public final int titleRes;
        public final int emptyRes;

        Mode(int titleRes, int emptyRes) {
            this.titleRes = titleRes;
            this.emptyRes = emptyRes;
        }
    }

    private final MutableLiveData<Mode> mode = new MutableLiveData<>(Mode.BATCHES);
    private final MutableLiveData<List<InventoryRow>> items =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<PagingUiState> pagingState =
            new MutableLiveData<>(PagingUiState.initial());
    private final MutableLiveData<Boolean> refreshing = new MutableLiveData<>(false);
    private final MutableLiveData<String> search = new MutableLiveData<>(null);
    private final MutableLiveData<String> summary = new MutableLiveData<>();
    private final MutableLiveData<Boolean> hasSummary = new MutableLiveData<>(false);

    @Nullable
    private PagedAccumulator<InventoryRow> accumulator;
    private boolean requestInFlight;

    @Inject
    public InventoryViewModel(@NonNull InventoryRepository inventoryRepository,
                              @NonNull ApiErrorMapper errorMapper) {
        this.inventoryRepository = inventoryRepository;
        this.errorMapper = errorMapper;
    }

    @NonNull
    public LiveData<Mode> mode() {
        return mode;
    }

    @NonNull
    public LiveData<List<InventoryRow>> items() {
        return items;
    }

    @NonNull
    public LiveData<PagingUiState> pagingState() {
        return pagingState;
    }

    @NonNull
    public LiveData<Boolean> refreshing() {
        return refreshing;
    }

    /** Valuation total for the rows loaded so far; null in the other modes. */
    @NonNull
    public LiveData<String> summary() {
        return summary;
    }

    @NonNull
    public LiveData<Boolean> hasSummary() {
        return hasSummary;
    }

    public void selectMode(@NonNull Mode next) {
        Mode current = mode.getValue();
        if (current == next) {
            return;
        }
        mode.setValue(next);
        // Clear the previous mode's rows immediately, otherwise the old list is briefly
        // shown under the new tab's title.
        items.setValue(Collections.emptyList());
        hasSummary.setValue(false);
        loadFirstPage();
    }

    public void onSearchChanged(@Nullable String text) {
        String trimmed = text == null ? "" : text.trim();
        search.setValue(trimmed.isEmpty() ? null : trimmed);
        loadFirstPage();
    }

    public void loadFirstPage() {
        if (requestInFlight) {
            return;
        }
        Mode current = mode.getValue();
        if (current == null) {
            return;
        }
        accumulator = new PagedAccumulator<>(sourceFor(current));
        requestInFlight = true;
        pagingState.setValue(PagingUiState.initial());
        run(accumulator.refresh(), true, current);
    }

    public void loadNextPage() {
        PagedAccumulator<InventoryRow> current = accumulator;
        if (requestInFlight || current == null || !current.canLoadMore()) {
            return;
        }
        requestInFlight = true;
        pagingState.setValue(PagingUiState.appending());
        run(current.loadNext(), false, mode.getValue());
    }

    public void refresh() {
        refreshing.setValue(true);
        loadFirstPage();
    }

    @NonNull
    private PagedSource<InventoryRow> sourceFor(@NonNull Mode current) {
        String query = search.getValue();
        switch (current) {
            case LOW_STOCK:
                return map(inventoryRepository.lowStock(query, errorMapper),
                        InventoryViewModel::mapLowStock);
            case EXPIRING:
                return map(inventoryRepository.expiring(DEFAULT_EXPIRING_DAYS, null, query,
                        errorMapper), InventoryViewModel::mapExpiring);
            case VALUATION:
                return map(inventoryRepository.valuationPaging(query, null, errorMapper),
                        valuation -> InventoryRow.of(valuation));
            case BATCHES:
            default:
                return map(inventoryRepository.batches(
                                InventoryRepository.Filter.batches(query), errorMapper),
                        InventoryViewModel::mapBatch);
        }
    }

    /**
     * Wraps a repository source so its payload becomes the mode-agnostic row the adapter
     * renders. Done here rather than in the repository so each repository stays a plain
     * mirror of its endpoint.
     */
    @NonNull
    private static <S> PagedSource<InventoryRow> map(@NonNull PageablePagingSource<S> source,
                                                     @NonNull io.reactivex.rxjava3.functions
                                                             .Function<S, InventoryRow> mapper) {
        return new MappedPagingSource<>(source, mapper, source.getPageSize());
    }

    private void run(@NonNull Single<PagedAccumulator.Outcome<InventoryRow>> call,
                     boolean firstPage,
                     @Nullable Mode current) {
        disposables.add(call
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        outcome -> {
                            requestInFlight = false;
                            refreshing.setValue(false);
                            items.setValue(outcome.getItems());
                            pagingState.setValue(outcome.getItems().isEmpty() && firstPage
                                    ? PagingUiState.empty()
                                    : PagingUiState.idle(outcome.isEndReached()));
                        },
                        throwable -> {
                            requestInFlight = false;
                            refreshing.setValue(false);
                            pagingState.setValue(PagingUiState.error(
                                    NetworkCall.asApiError(throwable)));
                        }));
    }

    /**
     * A batch with no stock left cannot be sold, so it is called out rather than hidden.
     */
    @NonNull
    static InventoryRow mapBatch(@NonNull StockBatchResponse batch) {
        return InventoryRow.of(batch, severityOf(batch));
    }

    @NonNull
    static InventoryRow mapLowStock(@NonNull LowStockDrugResponse drug) {
        return InventoryRow.of(drug);
    }

    @NonNull
    static InventoryRow mapExpiring(@NonNull ExpiringBatchResponse batch) {
        return InventoryRow.of(batch, alertOfExpiring(batch));
    }

    /** A batch with no stock left cannot be sold, so it is called out rather than hidden. */
    @Nullable
    private static InventoryRow.Alert severityOf(@NonNull StockBatchResponse batch) {
        if (batch.getRemainingQuantity() <= 0) {
            return InventoryRow.Alert.EXPIRED;
        }
        if (batch.getExpirationDate() == null) {
            return null;
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(
                java.time.LocalDate.now(com.lpms.core.util.Dates.clock()),
                batch.getExpirationDate());
        if (days < 0) {
            return InventoryRow.Alert.EXPIRED;
        }
        if (days <= CRITICAL_DAYS) {
            return InventoryRow.Alert.EXPIRING_SOON;
        }
        return null;
    }

    @Nullable
    private static InventoryRow.Alert alertOfExpiring(@NonNull ExpiringBatchResponse batch) {
        if (batch.isExpired()) {
            return InventoryRow.Alert.EXPIRED;
        }
        Long days = batch.getDaysUntilExpiration();
        return days != null && days <= CRITICAL_DAYS
                ? InventoryRow.Alert.EXPIRING_SOON : null;
    }

    @Override
    protected void onCleared() {
        disposables.clear();
    }
}