package com.lpms.ui.reports;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.R;
import com.lpms.core.error.ApiErrorMapper;
import com.lpms.core.network.NetworkCall;
import com.lpms.core.network.PagedAccumulator;
import com.lpms.core.ui.UiState;
import com.lpms.data.dto.ProfitDetailResponse;
import com.lpms.data.dto.ProfitPreset;
import com.lpms.data.dto.ProfitReportResponse;
import com.lpms.data.repo.ReportRepository;
import com.lpms.ui.common.FormError;

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
 * Profit reports: a headline summary for a period, and the line-level breakdown behind it.
 *
 * <p>The period is one of the backend's named presets, or an explicit range. Both the
 * summary ({@code GET /reports/profit}) and the breakdown
 * ({@code GET /reports/profit/details}) are read for the same dates, so changing the period
 * reloads both together rather than letting them drift apart.</p>
 */
@HiltViewModel
public final class ReportsViewModel extends ViewModel {

    private final ReportRepository reportRepository;
    private final ApiErrorMapper errorMapper;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<UiState<ProfitReportResponse>> summary =
            new MutableLiveData<>();
    private final MutableLiveData<ProfitPreset> preset =
            new MutableLiveData<>(ProfitPreset.DEFAULT);
    private final MutableLiveData<LocalDate> customFrom = new MutableLiveData<>();
    private final MutableLiveData<LocalDate> customTo = new MutableLiveData<>();

    private final MutableLiveData<List<ProfitDetailResponse>> breakdown =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> loadingMoreBreakdown = new MutableLiveData<>(false);
    private final MutableLiveData<FormError> message = new MutableLiveData<>();

    @Nullable
    private PagedAccumulator<ProfitDetailResponse> breakdownAccumulator;
    private boolean summaryInFlight;
    private boolean breakdownInFlight;

    @Inject
    public ReportsViewModel(@NonNull ReportRepository reportRepository,
                            @NonNull ApiErrorMapper errorMapper) {
        this.reportRepository = reportRepository;
        this.errorMapper = errorMapper;
    }

    @NonNull
    public LiveData<UiState<ProfitReportResponse>> summary() {
        return summary;
    }

    @NonNull
    public LiveData<ProfitPreset> preset() {
        return preset;
    }

    @NonNull
    public LiveData<List<ProfitDetailResponse>> breakdown() {
        return breakdown;
    }

    @NonNull
    public LiveData<Boolean> loadingMoreBreakdown() {
        return loadingMoreBreakdown;
    }

    @NonNull
    public LiveData<FormError> message() {
        return message;
    }

    /** The dates currently on screen, for the period label. */
    @NonNull
    public ProfitPreset.DateRange currentRange() {
        ProfitPreset selected = preset.getValue();
        if (selected != null) {
            return reportRepository.resolve(selected);
        }
        LocalDate from = customFrom.getValue();
        LocalDate to = customTo.getValue();
        return new ProfitPreset.DateRange(
                from == null ? LocalDate.now() : from,
                to == null ? LocalDate.now() : to);
    }

    public void load() {
        fetchSummary();
        loadFirstBreakdownPage();
    }

    public void selectPreset(@NonNull ProfitPreset next) {
        ProfitPreset current = preset.getValue();
        if (current == next) {
            return;
        }
        preset.setValue(next);
        load();
    }

    /** Switches to an explicit range, clearing the preset so it is not also sent. */
    public void selectCustomRange(@Nullable LocalDate from, @Nullable LocalDate to) {
        if (from == null || to == null) {
            message.setValue(FormError.of(R.string.reports_error_range_required));
            return;
        }
        if (from.isAfter(to)) {
            message.setValue(FormError.of(R.string.error_range_inverted));
            return;
        }
        preset.setValue(null);
        customFrom.setValue(from);
        customTo.setValue(to);
        load();
    }

    public void refresh() {
        load();
    }

    private void fetchSummary() {
        if (summaryInFlight) {
            return;
        }
        summaryInFlight = true;
        ProfitPreset selected = preset.getValue();
        ProfitPreset.DateRange range = currentRange();

        Single<ProfitReportResponse> call = selected != null
                ? reportRepository.range(selected, null, null)
                : reportRepository.range(null, range.getFrom(), range.getTo());

        disposables.add(call
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        report -> {
                            summaryInFlight = false;
                            summary.setValue(UiState.content(report));
                        },
                        throwable -> {
                            summaryInFlight = false;
                            summary.setValue(UiState.error(NetworkCall.asApiError(throwable)));
                        }));
    }

    private void loadFirstBreakdownPage() {
        if (breakdownInFlight) {
            return;
        }
        ProfitPreset.DateRange range = currentRange();
        breakdownAccumulator = new PagedAccumulator<>(reportRepository.details(
                range.getFrom(), range.getTo(), null, null, null, null, null, errorMapper));
        breakdownInFlight = true;
        disposables.add(breakdownAccumulator.refresh()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        outcome -> {
                            breakdownInFlight = false;
                            breakdown.setValue(outcome.getItems());
                        },
                        throwable -> {
                            breakdownInFlight = false;
                            // The summary is the point of this screen; a failing breakdown
                            // must not replace it with an error state.
                            message.setValue(FormError.of(
                                    NetworkCall.asApiError(throwable).getMessage()));
                        }));
    }

    public void loadMoreBreakdown() {
        PagedAccumulator<ProfitDetailResponse> current = breakdownAccumulator;
        if (breakdownInFlight || current == null || !current.canLoadMore()) {
            return;
        }
        breakdownInFlight = true;
        loadingMoreBreakdown.setValue(true);
        disposables.add(current.loadNext()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        outcome -> {
                            breakdownInFlight = false;
                            loadingMoreBreakdown.setValue(false);
                            breakdown.setValue(outcome.getItems());
                        },
                        throwable -> {
                            breakdownInFlight = false;
                            loadingMoreBreakdown.setValue(false);
                            message.setValue(FormError.of(
                                    NetworkCall.asApiError(throwable).getMessage()));
                        }));
    }

    public boolean hasMoreBreakdown() {
        return breakdownAccumulator != null && breakdownAccumulator.canLoadMore();
    }

    @Override
    protected void onCleared() {
        disposables.clear();
    }
}