package com.lpms.data.repo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.core.error.ApiErrorMapper;
import com.lpms.core.network.NetworkCall;
import com.lpms.core.network.PageablePagingSource;
import com.lpms.core.util.AppPreferences;
import com.lpms.data.api.SalesApi;
import com.lpms.data.dto.CreateRefundRequest;
import com.lpms.data.dto.CreateSaleItemRequest;
import com.lpms.data.dto.CreateSaleRequest;
import com.lpms.data.dto.PageResponse;
import com.lpms.data.dto.PaymentMethod;
import com.lpms.data.dto.RefundResponse;
import com.lpms.data.dto.SaleResponse;
import com.lpms.data.dto.TransferDetails;
import com.lpms.local.LocalBackend;
import com.lpms.local.LocalPagingSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import retrofit2.Call;

/**
 * Sales: create, read, refund, and the filtered history list.
 *
 * <p><b>The backend has no idempotency key</b>, so {@link #create} must run exactly once
 * per confirmation. The caller disables its confirm button while the request is in
 * flight and never auto-retries; after a timeout it re-reads {@code GET /sales} before
 * allowing another attempt, so a duplicate sale cannot be created.</p>
 */
@Singleton
public final class SalesRepository {

    private final SalesApi salesApi;
    private final LocalBackend localBackend;
    private final AppPreferences preferences;

    @Inject
    public SalesRepository(@NonNull SalesApi salesApi,
                           @NonNull LocalBackend localBackend,
                           @NonNull AppPreferences preferences) {
        this.salesApi = salesApi;
        this.localBackend = localBackend;
        this.preferences = preferences;
    }

    /**
     * Immutable, value-compared history filter. Changing it produces a new
     * {@link #paging} source, which restarts Paging at page 0 with no manual
     * invalidation.
     */
    public static final class Filter {

        @Nullable
        final Long customerId;
        @Nullable
        final String paymentMethod;
        @Nullable
        final String status;
        @Nullable
        final LocalDate from;
        @Nullable
        final LocalDate to;
        @Nullable
        final String sort;

        public Filter(@Nullable Long customerId,
                      @Nullable String paymentMethod,
                      @Nullable String status,
                      @Nullable LocalDate from,
                      @Nullable LocalDate to,
                      @Nullable String sort) {
            this.customerId = customerId;
            this.paymentMethod = paymentMethod;
            this.status = status;
            this.from = from;
            this.to = to;
            this.sort = sort;
        }

        public static Filter none() {
            return new Filter(null, null, null, null, null, null);
        }

        @Nullable
        public Long getCustomerId() {
            return customerId;
        }

        @Nullable
        public String getPaymentMethod() {
            return paymentMethod;
        }

        @Nullable
        public String getStatus() {
            return status;
        }

        @Nullable
        public LocalDate getFrom() {
            return from;
        }

        @Nullable
        public LocalDate getTo() {
            return to;
        }

        @Nullable
        public String getSort() {
            return sort;
        }

        @NonNull
        public Filter withCustomerId(@Nullable Long value) {
            return new Filter(value, paymentMethod, status, from, to, sort);
        }

        @NonNull
        public Filter withPaymentMethod(@Nullable String value) {
            return new Filter(customerId, value, status, from, to, sort);
        }

        @NonNull
        public Filter withStatus(@Nullable String value) {
            return new Filter(customerId, paymentMethod, value, from, to, sort);
        }

        @NonNull
        public Filter withRange(@Nullable LocalDate newFrom, @Nullable LocalDate newTo) {
            return new Filter(customerId, paymentMethod, status, newFrom, newTo, sort);
        }

        @NonNull
        public Filter withSort(@Nullable String value) {
            return new Filter(customerId, paymentMethod, status, from, to, value);
        }

        @NonNull
        public Filter cleared() {
            return none();
        }

        /** True when nothing is filtered, so the UI can hide the "clear" action. */
        public boolean isEmpty() {
            return customerId == null && paymentMethod == null && status == null
                    && from == null && to == null && sort == null;
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Filter)) {
                return false;
            }
            Filter that = (Filter) other;
            return java.util.Objects.equals(customerId, that.customerId)
                    && java.util.Objects.equals(paymentMethod, that.paymentMethod)
                    && java.util.Objects.equals(status, that.status)
                    && java.util.Objects.equals(from, that.from)
                    && java.util.Objects.equals(to, that.to)
                    && java.util.Objects.equals(sort, that.sort);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(customerId, paymentMethod, status, from, to, sort);
        }
    }

    private final class SalePagingSource extends PageablePagingSource<SaleResponse> {

        private final Filter filter;

        SalePagingSource(@NonNull Filter filter, @NonNull ApiErrorMapper errorMapper) {
            super(errorMapper, DEFAULT_PAGE_SIZE);
            this.filter = filter;
        }

        @NonNull
        @Override
        protected Call<PageResponse<SaleResponse>> callForPage(int page, int size) {
            return salesApi.list(
                    filter.getCustomerId(),
                    filter.getPaymentMethod(),
                    filter.getStatus(),
                    filter.getFrom(),
                    filter.getTo(),
                    page,
                    size,
                    filter.getSort());
        }
    }

    /** Sales history, filtered. See {@link Filter}. */
    @NonNull
    public PageablePagingSource<SaleResponse> paging(@NonNull Filter filter,
                                                    @NonNull ApiErrorMapper errorMapper) {
        if (preferences.isOffline()) {
            return new LocalPagingSource<>(
                    localBackend.observeSales().firstOrError()
                            .map(rows -> applyFilter(rows, filter)),
                    errorMapper);
        }
        return new SalePagingSource(filter, errorMapper);
    }

    /** Applies the history filters to the locally-recorded sales. */
    @NonNull
    private static List<SaleResponse> applyFilter(@NonNull List<SaleResponse> rows,
                                                  @NonNull Filter filter) {
        List<SaleResponse> out = new java.util.ArrayList<>();
        for (SaleResponse row : rows) {
            if (filter.getCustomerId() != null
                    && !filter.getCustomerId().equals(row.getCustomerId())) {
                continue;
            }
            String method = row.getPaymentMethod();
            if (filter.getPaymentMethod() != null && !filter.getPaymentMethod().isEmpty()
                    && (method == null || !filter.getPaymentMethod().equalsIgnoreCase(method))) {
                continue;
            }
            com.lpms.data.dto.SaleStatus status = row.saleStatus();
            if (filter.getStatus() != null && !filter.getStatus().isEmpty()
                    && (status == null || !filter.getStatus().equalsIgnoreCase(status.name()))) {
                continue;
            }
            out.add(row);
        }
        return out;
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
                                       @NonNull BigDecimal amountPaid,
                                       @Nullable TransferDetails transfer) {
        if (preferences.isOffline()) {
            return localBackend.recordSale(customerId, customerNameFor(customerId),
                    paymentMethod, toLocalLines(items), discount, amountPaid);
        }
        return salesApi.create(requestFor(customerId, paymentMethod, items, discount, amountPaid,
                transfer))
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    /** Translates the request lines into the local writer's shape. */
    @NonNull
    private static List<LocalBackend.CartLine> toLocalLines(
            @NonNull List<CreateSaleItemRequest> items) {
        List<LocalBackend.CartLine> out = new java.util.ArrayList<>(items.size());
        for (CreateSaleItemRequest item : items) {
            out.add(new LocalBackend.CartLine(item.getDrugId(),
                    item.getQuantity() == null ? 0 : item.getQuantity(),
                    item.getUnitSellingPrice()));
        }
        return out;
    }

    @Nullable
    private String customerNameFor(@Nullable Long customerId) {
        if (customerId == null) {
            return null;
        }
        try {
            com.lpms.data.dto.CustomerResponse customer =
                    localBackend.customer(customerId).blockingGet();
            return customer == null ? null : customer.getName();
        } catch (RuntimeException missing) {
            return null;
        }
    }

    @NonNull
    public Single<SaleResponse> get(long id) {
        if (preferences.isOffline()) {
            return localBackend.sale(id).toSingle()
                    .onErrorResumeNext(error -> Single.error(new com.lpms.core.error.ApiException(
                            com.lpms.core.error.ApiError.of(404, "NOT_FOUND",
                                    "No sale " + id + " in the local database"))));
        }
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
                                               @NonNull BigDecimal amountPaid,
                                               @Nullable TransferDetails transfer) {
        // Only a bank transfer may carry a destination; sending it on any other method is
        // rejected by the server, so it is dropped here rather than relying on the caller.
        TransferDetails sent = paymentMethod == PaymentMethod.BANK_TRANSFER ? transfer : null;
        return new CreateSaleRequest(customerId, paymentMethod.wireValue(), items, discount,
                amountPaid, sent);
    }

    /** Overload for payments that can never carry transfer details. */
    @NonNull
    public static CreateSaleRequest requestFor(@Nullable Long customerId,
                                               @NonNull PaymentMethod paymentMethod,
                                               @NonNull List<CreateSaleItemRequest> items,
                                               @NonNull BigDecimal discount,
                                               @NonNull BigDecimal amountPaid) {
        return requestFor(customerId, paymentMethod, items, discount, amountPaid, null);
    }
}