package com.lpms.ui.audit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.core.error.ApiErrorMapper;
import com.lpms.core.network.NetworkCall;
import com.lpms.core.network.PagedAccumulator;
import com.lpms.data.dto.AuditLogResponse;
import com.lpms.data.repo.AuditRepository;
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

/** Audit log: read-only, filtered by action and date range. */
@HiltViewModel
public final class AuditListViewModel extends ViewModel {

    private final AuditRepository auditRepository;
    private final ApiErrorMapper errorMapper;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<AuditRepository.Filter> filter =
            new MutableLiveData<>(AuditRepository.Filter.none());
    private final MutableLiveData<List<AuditLogResponse>> items =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<PagingUiState> pagingState =
            new MutableLiveData<>(PagingUiState.initial());
    private final MutableLiveData<Boolean> refreshing = new MutableLiveData<>(false);
    private final MutableLiveData<FormError> message = new MutableLiveData<>();

    @Nullable
    private PagedAccumulator<AuditLogResponse> accumulator;
    private boolean requestInFlight;

    @Inject
    public AuditListViewModel(@NonNull AuditRepository auditRepository,
                              @NonNull ApiErrorMapper errorMapper) {
        this.auditRepository = auditRepository;
        this.errorMapper = errorMapper;
    }

    @NonNull
    public LiveData<List<AuditLogResponse>> items() {
        return items;
    }

    @NonNull
    public LiveData<PagingUiState> pagingState() {
        return pagingState;
    }

    @NonNull
    public LiveData<AuditRepository.Filter> filter() {
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

    public void loadFirstPage() {
        if (requestInFlight) {
            return;
        }
        accumulator = new PagedAccumulator<>(auditRepository.paging(filter.getValue(), errorMapper));
        requestInFlight = true;
        pagingState.setValue(PagingUiState.initial());
        run(accumulator.refresh(), true);
    }

    public void loadNextPage() {
        PagedAccumulator<AuditLogResponse> current = accumulator;
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

    private void run(@NonNull Single<PagedAccumulator.Outcome<AuditLogResponse>> call,
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

    public void setAction(@Nullable String wireValue) {
        applyFilter(filter.getValue().withAction(
                wireValue == null || wireValue.isEmpty() ? null : wireValue));
    }

    public void setRange(@Nullable LocalDate from, @Nullable LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            message.setValue(FormError.of(com.lpms.R.string.error_range_inverted));
            return;
        }
        applyFilter(filter.getValue().withRange(from, to));
    }

    public void clearFilters() {
        applyFilter(AuditRepository.Filter.none());
    }

    private void applyFilter(@NonNull AuditRepository.Filter next) {
        filter.setValue(next);
        loadFirstPage();
    }

    @Override
    protected void onCleared() {
        disposables.clear();
    }
}