package com.lpms.ui.sales;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.R;
import com.lpms.core.auth.Role;
import com.lpms.core.auth.Session;
import com.lpms.core.auth.SessionManager;
import com.lpms.core.error.ApiErrorMapper;
import com.lpms.core.network.NetworkCall;
import com.lpms.core.network.PagedAccumulator;
import com.lpms.data.dto.SaleResponse;
import com.lpms.data.repo.AuthRepository;
import com.lpms.data.repo.SalesRepository;
import com.lpms.ui.common.FormError;
import com.lpms.ui.common.PagingUiState;

import java.time.LocalDate;
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
 * Sales history: the filtered, paged list of sales.
 *
 * <p>This screen is also the recovery path the POS points at. When a sale cannot be
 * confirmed because the connection dropped, {@code POST /sales} may or may not have
 * reached the server, and there is no idempotency key that would make a retry safe. The
 * cashier lands here, finds their sale by time or customer, and only then decides to sell
 * again &mdash; so the list is never served from a stale cache.</p>
 */
@HiltViewModel
public final class SalesListViewModel extends ViewModel {

    private final SalesRepository salesRepository;
    private final ApiErrorMapper errorMapper;
    private final SessionManager sessionManager;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<SalesRepository.Filter> filter =
            new MutableLiveData<>(SalesRepository.Filter.none());
    private final MutableLiveData<List<SaleResponse>> items =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<PagingUiState> pagingState =
            new MutableLiveData<>(PagingUiState.initial());
    private final MutableLiveData<Boolean> refreshing = new MutableLiveData<>(false);
    private final MutableLiveData<FormError> message = new MutableLiveData<>();

    /**
     * A sale the caller wants highlighted, e.g. right after an uncertain confirmation.
     * Only a scroll hint: it costs no extra request.
     */
    private final MutableLiveData<Long> highlightSaleId = new MutableLiveData<>();

    @Nullable
    private PagedAccumulator<SaleResponse> accumulator;
    private boolean requestInFlight;

    @Inject
    public SalesListViewModel(@NonNull SalesRepository salesRepository,
                              @NonNull ApiErrorMapper errorMapper,
                              @NonNull AuthRepository authRepository) {
        this.salesRepository = salesRepository;
        this.errorMapper = errorMapper;
        this.sessionManager = authRepository.sessions();
    }

    @NonNull
    public LiveData<List<SaleResponse>> items() {
        return items;
    }

    @NonNull
    public LiveData<PagingUiState> pagingState() {
        return pagingState;
    }

    @NonNull
    public LiveData<SalesRepository.Filter> filter() {
        return filter;
    }

    @NonNull
    public LiveData<Boolean> refreshing() {
        return refreshing;
    }

    @NonNull
    public LiveData<FormError> message() {
        return message;
    }

    @NonNull
    public LiveData<Long> highlightSaleId() {
        return highlightSaleId;
    }

    /**
     * Cost and profit are part of the payload for every role, so the row must hide them for
     * EMPLOYEE rather than rely on the server omitting them.
     */
    public boolean canSeeProfit() {
        return role().canSeeProfit();
    }

    @NonNull
    public Role role() {
        Session session = sessionManager.current();
        return session == null ? Role.EMPLOYEE : session.getRole();
    }

    // ------------------------------------------------------------------- paging

    /** First page; also the retry action. */
    public void loadFirstPage() {
        if (requestInFlight) {
            return;
        }
        accumulator = new PagedAccumulator<>(
                salesRepository.paging(filter.getValue(), errorMapper));
        requestInFlight = true;
        pagingState.setValue(PagingUiState.initial());
        run(accumulator.refresh(), true);
    }

    public void loadNextPage() {
        PagedAccumulator<SaleResponse> current = accumulator;
        if (requestInFlight || current == null || !current.canLoadMore()) {
            return;
        }
        requestInFlight = true;
        pagingState.setValue(PagingUiState.appending());
        run(current.loadNext(), false);
    }

    public void refresh() {
        refreshing.setValue(true);
        loadFirstPage();
    }

    private void run(@NonNull Single<PagedAccumulator.Outcome<SaleResponse>> call,
                     boolean firstPage) {
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

    // ------------------------------------------------------------- filter edits

    public void setPaymentMethod(@Nullable String wireValue) {
        applyFilter(filter.getValue().withPaymentMethod(
                wireValue == null || wireValue.isEmpty() ? null : wireValue));
    }

    public void setStatus(@Nullable String wireValue) {
        applyFilter(filter.getValue().withStatus(
                wireValue == null || wireValue.isEmpty() ? null : wireValue));
    }

    public void setCustomerId(@Nullable Long customerId) {
        applyFilter(filter.getValue().withCustomerId(customerId));
    }

    /** {@code from}/{@code to} are inclusive, and the backend rejects from > to. */
    public void setRange(@Nullable LocalDate from, @Nullable LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            message.setValue(FormError.of(R.string.error_range_inverted));
            return;
        }
        applyFilter(filter.getValue().withRange(from, to));
    }

    public void clearFilters() {
        applyFilter(SalesRepository.Filter.none());
    }

    public void highlight(@Nullable Long saleId) {
        highlightSaleId.setValue(saleId);
    }

    private void applyFilter(@NonNull SalesRepository.Filter next) {
        filter.setValue(next);
        loadFirstPage();
    }

    @Override
    protected void onCleared() {
        disposables.clear();
    }
}