package com.lpms.data.repo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.core.network.NetworkCall;
import com.lpms.core.network.PageablePagingSource;
import com.lpms.core.error.ApiErrorMapper;
import com.lpms.data.api.CustomerApi;
import com.lpms.data.dto.CreateAdjustmentRequest;
import com.lpms.data.dto.CreateCustomerRequest;
import com.lpms.data.dto.CreatePaymentRequest;
import com.lpms.data.dto.CustomerAccountResponse;
import com.lpms.data.dto.CustomerResponse;
import com.lpms.data.dto.PageResponse;
import com.lpms.data.dto.PaymentResponse;
import com.lpms.data.dto.TransactionResponse;
import com.lpms.data.dto.UpdateCustomerRequest;

import javax.inject.Inject;
import java.util.List;

import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import retrofit2.Call;

/** Customers CRUD. Every role may create/edit customers and record payments. */
@Singleton
public final class CustomerRepository {

    private final CustomerApi customerApi;

    @Inject
    public CustomerRepository(@NonNull CustomerApi customerApi) {
        this.customerApi = customerApi;
    }

    @NonNull
    public PageablePagingSource<CustomerResponse> paging(@NonNull Filter filter,
                                                         @NonNull ApiErrorMapper errorMapper) {
        return new CustomerPagingSource(filter, errorMapper);
    }

    /** Convenience for the POS picker: first page of a name/phone search. */
    @NonNull
    public Single<List<CustomerResponse>> search(@Nullable String search, int size) {
        Single<PageResponse<CustomerResponse>> page = NetworkCall.body(
                customerApi.list(search, true, 0, size, "name,asc"),
                new ApiErrorMapper(com.lpms.core.network.json.LpmsGson.get()));
        return page.map(PageResponse::getContent)
                .map(rows -> (List<CustomerResponse>) rows)
                .subscribeOn(Schedulers.io());
    }

    @NonNull
    public Single<CustomerResponse> get(long id) {
        return customerApi.get(id)
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    /**
     * Statement: totals plus a page of transactions.
     *
     * <p>The transactions are paged <em>inside</em> this response rather than through a
     * separate endpoint, so the screen re-reads the whole account with a larger
     * {@code transactionPage} instead of calling an endpoint that does not exist.</p>
     */
    @NonNull
    public Single<CustomerAccountResponse> account(long id, int transactionPage,
                                                  int transactionSize) {
        return customerApi.account(id, transactionPage, transactionSize)
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    /** Records a payment against the debt. Never retried automatically. */
    @NonNull
    public Single<PaymentResponse> recordPayment(long id, @NonNull CreatePaymentRequest request) {
        return customerApi.recordPayment(id, request)
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    /** Manual correction. {@code direction} is DEBIT or CREDIT; both are audited. */
    @NonNull
    public Single<TransactionResponse> adjust(long id, @NonNull CreateAdjustmentRequest request) {
        return customerApi.adjust(id, request)
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    @NonNull
    public Single<CustomerResponse> create(@NonNull CreateCustomerRequest request) {
        return customerApi.create(request)
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    @NonNull
    public Single<CustomerResponse> update(long id, @NonNull UpdateCustomerRequest request) {
        return customerApi.update(id, request)
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    /** Soft delete: the ledger is preserved. */
    @NonNull
    public Completable deactivate(long id) {
        return customerApi.deactivate(id)
                .onErrorResumeNext(error -> Completable.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    /** Query state for {@code GET /customers}; {@code search} matches name or phone. */
    public static final class Filter {

        private final String search;
        private final Boolean active;
        private final String sort;

        public Filter(@Nullable String search, @Nullable Boolean active, @Nullable String sort) {
            this.search = search;
            this.active = active;
            this.sort = sort;
        }

        @NonNull
        public static Filter none() {
            return new Filter(null, null, "name,asc");
        }

        @Nullable
        public String getSearch() {
            return search;
        }

        @Nullable
        public Boolean getActive() {
            return active;
        }

        @Nullable
        public String getSort() {
            return sort;
        }

        @NonNull
        public Filter withSearch(@Nullable String value) {
            return new Filter(value, active, sort);
        }

        @NonNull
        public Filter withActive(@Nullable Boolean value) {
            return new Filter(search, value, sort);
        }

        @NonNull
        public Filter withSort(@Nullable String value) {
            return new Filter(search, active, value);
        }

        /** True when anything narrows the list, so the UI can show its "clear" action. */
        public boolean hasAnyFilter() {
            return search != null || active != null;
        }
    }

    private final class CustomerPagingSource extends PageablePagingSource<CustomerResponse> {

        private final Filter filter;

        CustomerPagingSource(@NonNull Filter filter, @NonNull ApiErrorMapper errorMapper) {
            super(errorMapper, DEFAULT_PAGE_SIZE);
            this.filter = filter;
        }

        @NonNull
        @Override
        protected Call<PageResponse<CustomerResponse>> callForPage(int page, int size) {
            return customerApi.list(filter.getSearch(), filter.getActive(), page, size,
                    filter.getSort());
        }
    }
}