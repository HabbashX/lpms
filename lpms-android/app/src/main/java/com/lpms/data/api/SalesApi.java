package com.lpms.data.api;

import androidx.annotation.Nullable;

import com.lpms.data.dto.CreateRefundRequest;
import com.lpms.data.dto.CreateSaleRequest;
import com.lpms.data.dto.PageResponse;
import com.lpms.data.dto.RefundResponse;
import com.lpms.data.dto.SaleResponse;

import java.time.LocalDate;

import io.reactivex.rxjava3.core.Single;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * {@code /api/v1/sales}.
 *
 * <p>Sortable: {@code id, createdAt, total, profitTotal, status} (default
 * {@code createdAt,desc}). {@code from}/{@code to} are inclusive {@code yyyy-MM-dd}
 * dates.</p>
 *
 * <p><b>No idempotency key exists on this backend.</b> The client therefore disables
 * the confirm button while a create/refund is in flight, never auto-retries a POST, and
 * re-reads {@code GET /sales} after a timeout before allowing another attempt.</p>
 */
public interface SalesApi {

    @POST("api/v1/sales")
    Single<SaleResponse> create(@Body CreateSaleRequest request);

    @GET("api/v1/sales")
    Call<PageResponse<SaleResponse>> list(@Nullable @Query("customerId") Long customerId,
                                          @Nullable @Query("paymentMethod") String paymentMethod,
                                          @Nullable @Query("status") String status,
                                          @Nullable @Query("from") LocalDate from,
                                          @Nullable @Query("to") LocalDate to,
                                          @Query("page") int page,
                                          @Query("size") int size,
                                          @Nullable @Query("sort") String sort);

    @GET("api/v1/sales/{id}")
    Single<SaleResponse> get(@Path("id") long id);

    /**
     * ADMIN/PHARMACIST. Omitting {@code items} refunds everything still refundable.
     * There is no refunds-list endpoint: after this call the client re-reads the sale
     * for its new status and {@code refundedTotal}.
     */
    @POST("api/v1/sales/{id}/refund")
    Single<RefundResponse> refund(@Path("id") long id, @Body CreateRefundRequest request);
}