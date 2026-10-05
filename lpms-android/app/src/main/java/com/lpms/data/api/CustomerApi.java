package com.lpms.data.api;

import androidx.annotation.Nullable;

import com.lpms.data.dto.CreateAdjustmentRequest;
import com.lpms.data.dto.CreateCustomerRequest;
import com.lpms.data.dto.CreatePaymentRequest;
import com.lpms.data.dto.CustomerAccountResponse;
import com.lpms.data.dto.CustomerResponse;
import com.lpms.data.dto.PageResponse;
import com.lpms.data.dto.PaymentResponse;
import com.lpms.data.dto.TransactionResponse;
import com.lpms.data.dto.UpdateCustomerRequest;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * {@code /api/v1/customers}.
 *
 * <p>Sortable: {@code id, name, phone, createdAt, updatedAt} (default
 * {@code name,asc}); {@code search} matches name or phone.</p>
 *
 * <p>Every role may create/edit/deactivate customers and record payments; only
 * ADMIN/PHARMACIST may post ledger {@link CreateAdjustmentRequest adjustments}.</p>
 */
public interface CustomerApi {

    @GET("api/v1/customers")
    Call<PageResponse<CustomerResponse>> list(@Nullable @Query("search") String search,
                                              @Nullable @Query("active") Boolean active,
                                              @Query("page") int page,
                                              @Query("size") int size,
                                              @Nullable @Query("sort") String sort);

    @GET("api/v1/customers/{id}")
    Single<CustomerResponse> get(@Path("id") long id);

    @POST("api/v1/customers")
    Single<CustomerResponse> create(@Body CreateCustomerRequest request);

    @PUT("api/v1/customers/{id}")
    Single<CustomerResponse> update(@Path("id") long id, @Body UpdateCustomerRequest request);

    /** Soft delete: the ledger is preserved. */
    @DELETE("api/v1/customers/{id}")
    Completable deactivate(@Path("id") long id);

    /**
     * Debt summary plus the ledger page.
     *
     * <p>Note the pagination parameter names: {@code transactionPage} /
     * {@code transactionSize}, not {@code page} / {@code size}.</p>
     */
    @GET("api/v1/customers/{id}/account")
    Single<CustomerAccountResponse> account(@Path("id") long id,
                                            @Query("transactionPage") int transactionPage,
                                            @Query("transactionSize") int transactionSize);

    /** 409 {@code PAYMENT_EXCEEDS_DEBT} unless {@code customer.allow_negative_balance}. */
    @POST("api/v1/customers/{id}/payments")
    Single<PaymentResponse> recordPayment(@Path("id") long id,
                                          @Body CreatePaymentRequest request);

    /** ADMIN/PHARMACIST only. */
    @POST("api/v1/customers/{id}/adjustments")
    Single<TransactionResponse> adjust(@Path("id") long id,
                                       @Body CreateAdjustmentRequest request);
}