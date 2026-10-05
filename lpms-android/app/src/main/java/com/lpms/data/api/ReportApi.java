package com.lpms.data.api;

import androidx.annotation.Nullable;

import com.lpms.data.dto.DailyProfitResponse;
import com.lpms.data.dto.MonthlyProfitResponse;
import com.lpms.data.dto.PageResponse;
import com.lpms.data.dto.ProfitDetailResponse;
import com.lpms.data.dto.ProfitReportResponse;
import com.lpms.data.dto.WeeklyProfitResponse;

import java.time.LocalDate;

import io.reactivex.rxjava3.core.Single;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * {@code /api/v1/reports} â€” ADMIN/PHARMACIST only.
 *
 * <p>All dates are inclusive and sent as {@code yyyy-MM-dd}. An explicit
 * {@code from}/{@code to} pair overrides {@code preset}; sending nothing means today.</p>
 *
 * <p>The detail endpoint's filters are named {@code drug}, {@code category},
 * {@code employee} and {@code customer} (ids) â€” deliberately not {@code drugId} etc.</p>
 */
public interface ReportApi {

    @GET("api/v1/reports/profit/daily")
    Single<DailyProfitResponse> daily(@Query("date") LocalDate date);

    /** ISO week (Mondayâ€“Sunday) containing {@code date}. */
    @GET("api/v1/reports/profit/weekly")
    Single<WeeklyProfitResponse> weekly(@Query("date") LocalDate date);

    @GET("api/v1/reports/profit/monthly")
    Single<MonthlyProfitResponse> monthly(@Query("year") int year, @Query("month") int month);

    /** {@code preset} âˆˆ today, yesterday, this_week, last_week, this_month, last_month. */
    @GET("api/v1/reports/profit")
    Single<ProfitReportResponse> profit(@Nullable @Query("preset") String preset,
                                        @Nullable @Query("from") LocalDate from,
                                        @Nullable @Query("to") LocalDate to);

    @GET("api/v1/reports/profit/details")
    Call<PageResponse<ProfitDetailResponse>> details(@Nullable @Query("from") LocalDate from,
                                                      @Nullable @Query("to") LocalDate to,
                                                      @Nullable @Query("drug") Long drugId,
                                                      @Nullable @Query("category") Long categoryId,
                                                      @Nullable @Query("employee") Long employeeId,
                                                      @Nullable @Query("paymentMethod") String paymentMethod,
                                                      @Nullable @Query("customer") Long customerId,
                                                      @Query("page") int page,
                                                      @Query("size") int size);
}