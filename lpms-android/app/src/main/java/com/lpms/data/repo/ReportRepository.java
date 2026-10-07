package com.lpms.data.repo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.core.error.ApiErrorMapper;
import com.lpms.core.network.NetworkCall;
import com.lpms.core.network.PageablePagingSource;
import com.lpms.data.api.ReportApi;
import com.lpms.data.dto.DailyProfitResponse;
import com.lpms.data.dto.MonthlyProfitResponse;
import com.lpms.data.dto.PageResponse;
import com.lpms.data.dto.ProfitDetailResponse;
import com.lpms.data.dto.ProfitReportResponse;
import com.lpms.data.dto.ProfitPreset;
import com.lpms.data.dto.WeeklyProfitResponse;

import java.time.LocalDate;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import retrofit2.Call;

/**
 * Profit reports.
 *
 * <p>The summary endpoints ({@code /daily}, {@code /weekly}, {@code /monthly}) and the
 * range endpoint ({@code /profit}) all reduce to the same five numbers, so the screens work
 * against {@link ProfitReportResponse}: the period endpoints expose {@code asRange()} for
 * exactly that.</p>
 *
 * <p>All of these are reads. Nothing here is ever retried automatically, and no report
 * writes.</p>
 */
@Singleton
public final class ReportRepository {

    private final ReportApi reportApi;

    @Inject
    public ReportRepository(@NonNull ReportApi reportApi) {
        this.reportApi = reportApi;
    }

    /** Named range (today, last month, …) or an explicit from/to pair. */
    @NonNull
    public Single<ProfitReportResponse> range(@Nullable ProfitPreset preset,
                                              @Nullable LocalDate from,
                                              @Nullable LocalDate to) {
        Single<ProfitReportResponse> call = preset != null
                ? reportApi.profit(preset.wireValue(), null, null)
                : reportApi.profit(null, from, to);
        return call
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    /** Resolves a preset to concrete dates, so the UI can label the period it is showing. */
    @NonNull
    public ProfitPreset.DateRange resolve(@NonNull ProfitPreset preset) {
        return preset.resolve(com.lpms.core.util.Dates.clock());
    }

    @NonNull
    public Single<ProfitReportResponse> daily(@NonNull LocalDate date) {
        return reportApi.daily(date)
                .map(DailyProfitResponse::asRange)
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    @NonNull
    public Single<ProfitReportResponse> weekly(@NonNull LocalDate anyDayInWeek) {
        return reportApi.weekly(anyDayInWeek)
                .map(WeeklyProfitResponse::asRange)
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    @NonNull
    public Single<ProfitReportResponse> monthly(int year, int month) {
        return reportApi.monthly(year, month)
                .map(MonthlyProfitResponse::asRange)
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    /**
     * Line-level profit for a date range. Every filter is optional; the screen only sends
     * the ones it has a value for, because the endpoint treats each null as "any".
     */
    @NonNull
    public PageablePagingSource<ProfitDetailResponse> details(@Nullable LocalDate from,
                                                             @Nullable LocalDate to,
                                                             @Nullable Long drugId,
                                                             @Nullable Long categoryId,
                                                             @Nullable Long employeeId,
                                                             @Nullable String paymentMethod,
                                                             @Nullable Long customerId,
                                                             @NonNull ApiErrorMapper errorMapper) {
        return new DetailsPagingSource(from, to, drugId, categoryId, employeeId, paymentMethod,
                customerId, errorMapper);
    }

    private final class DetailsPagingSource extends PageablePagingSource<ProfitDetailResponse> {

        private final LocalDate from;
        private final LocalDate to;
        private final Long drugId;
        private final Long categoryId;
        private final Long employeeId;
        private final String paymentMethod;
        private final Long customerId;

        DetailsPagingSource(@Nullable LocalDate from, @Nullable LocalDate to,
                            @Nullable Long drugId, @Nullable Long categoryId,
                            @Nullable Long employeeId, @Nullable String paymentMethod,
                            @Nullable Long customerId, @NonNull ApiErrorMapper mapper) {
            super(mapper, DEFAULT_PAGE_SIZE);
            this.from = from;
            this.to = to;
            this.drugId = drugId;
            this.categoryId = categoryId;
            this.employeeId = employeeId;
            this.paymentMethod = paymentMethod;
            this.customerId = customerId;
        }

        @NonNull
        @Override
        protected Call<PageResponse<ProfitDetailResponse>> callForPage(int page, int size) {
            return reportApi.details(from, to, drugId, categoryId, employeeId, paymentMethod,
                    customerId, page, size);
        }
    }
}