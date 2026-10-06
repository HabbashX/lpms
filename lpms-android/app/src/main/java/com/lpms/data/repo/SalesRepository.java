package com.lpms.data.repo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.core.network.NetworkCall;
import com.lpms.data.api.SalesApi;
import com.lpms.data.dto.CreateRefundRequest;
import com.lpms.data.dto.CreateSaleItemRequest;
import com.lpms.data.dto.CreateSaleRequest;
import com.lpms.data.dto.PaymentMethod;
import com.lpms.data.dto.RefundResponse;
import com.lpms.data.dto.SaleResponse;

import java.math.BigDecimal;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Sales: create, read, refund.
 *
 * <p><b>The backend has no idempotency key</b>, so {@link #create} must run exactly once
 * per confirmation. The caller disables its confirm button while the request is in
 * flight and never auto-retries; after a timeout it re-reads {@code GET /sales} before
 * allowing another attempt, so a duplicate sale cannot be created.</p>
 */
@Singleton
public final class SalesRepository {

    private final SalesApi salesApi;

    @Inject
    public SalesRepository(@NonNull SalesApi salesApi) {
        this.salesApi = salesApi;
    }

    /**
     * @param customerId null for a walk-in sale
     * @param amountPaid 0…total; without a customer the sale must be paid in full
     */
    @NonNull
    public Single<SaleResponse> create(@Nullable Long customerId,
                                       @NonNull PaymentMethod paymentMethod,
                                       @NonNull List<CreateSaleItemRequest> items,
                                       @NonNull BigDecimal discount,
                                       @NonNull BigDecimal amountPaid) {
        return salesApi.create(requestFor(customerId, paymentMethod, items, discount, amountPaid))
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    @NonNull
    public Single<SaleResponse> get(long id) {
        return salesApi.get(id)
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    /**
     * ADMIN/PHARMACIST. An empty {@code items} list refunds everything still refundable;
     * after a refund there is no refunds-list endpoint, so the caller re-reads the sale.
     */
    @NonNull
    public Single<RefundResponse> refund(long saleId, @NonNull CreateRefundRequest request) {
        return salesApi.refund(saleId, request)
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    /** Builds the immutable request body; also used for local pre-flight validation. */
    @NonNull
    public static CreateSaleRequest requestFor(@Nullable Long customerId,
                                               @NonNull PaymentMethod paymentMethod,
                                               @NonNull List<CreateSaleItemRequest> items,
                                               @NonNull BigDecimal discount,
                                               @NonNull BigDecimal amountPaid) {
        return new CreateSaleRequest(customerId, paymentMethod.wireValue(), items, discount,
                amountPaid);
    }
}