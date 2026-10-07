package com.lpms.ui.customers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.R;
import com.lpms.core.error.ApiErrorMapper;
import com.lpms.core.network.NetworkCall;
import com.lpms.core.network.PagedAccumulator;
import com.lpms.data.dto.CustomerResponse;
import com.lpms.data.dto.UpdateCustomerRequest;
import com.lpms.data.repo.CustomerRepository;
import com.lpms.ui.common.FormError;
import com.lpms.ui.common.PagingUiState;

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
 * Customer list: search, active filter and paging.
 *
 * <p>The balance is deliberately absent. {@link CustomerResponse} has no debt field, and
 * resolving it per row would add one request per customer on every page; the statement
 * screen loads it once for the customer being opened.</p>
 */
@HiltViewModel
public final class CustomerListViewModel extends ViewModel {

    private final CustomerRepository customerRepository;
    private final ApiErrorMapper errorMapper;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<CustomerRepository.Filter> filter =
            new MutableLiveData<>(CustomerRepository.Filter.none());
    private final MutableLiveData<List<CustomerResponse>> items =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<PagingUiState> pagingState =
            new MutableLiveData<>(PagingUiState.initial());
    private final MutableLiveData<Boolean> refreshing = new MutableLiveData<>(false);
    private final MutableLiveData<FormError> message = new MutableLiveData<>();

    @Nullable
    private PagedAccumulator<CustomerResponse> accumulator;
    private boolean requestInFlight;

    @Inject
    public CustomerListViewModel(@NonNull CustomerRepository customerRepository,
                                 @NonNull ApiErrorMapper errorMapper) {
        this.customerRepository = customerRepository;
        this.errorMapper = errorMapper;
    }

    @NonNull
    public LiveData<List<CustomerResponse>> items() {
        return items;
    }

    @NonNull
    public LiveData<PagingUiState> pagingState() {
        return pagingState;
    }

    @NonNull
    public LiveData<CustomerRepository.Filter> filter() {
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
        accumulator = new PagedAccumulator<>(
                customerRepository.paging(filter.getValue(), errorMapper));
        requestInFlight = true;
        pagingState.setValue(PagingUiState.initial());
        run(accumulator.refresh(), true);
    }

    public void loadNextPage() {
        PagedAccumulator<CustomerResponse> current = accumulator;
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

    private void run(@NonNull Single<PagedAccumulator.Outcome<CustomerResponse>> call,
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

    public void onSearchChanged(@Nullable String text) {
        String trimmed = text == null ? "" : text.trim();
        applyFilter(filter.getValue().withSearch(trimmed.isEmpty() ? null : trimmed));
    }

    /** null = active and inactive, true = active only, false = inactive only. */
    public void setActive(@Nullable Boolean active) {
        applyFilter(filter.getValue().withActive(active));
    }

    public void clearFilters() {
        applyFilter(CustomerRepository.Filter.none());
    }

    private void applyFilter(@NonNull CustomerRepository.Filter next) {
        filter.setValue(next);
        loadFirstPage();
    }

    /**
     * Deactivate is a soft delete ({@code DELETE /customers/{id}}); reactivating is a normal
     * update that carries the current fields so nothing else is lost.
     */
    public void setCustomerActive(@NonNull CustomerResponse customer, boolean active) {
        if (customer.getId() == null) {
            return;
        }
        long id = customer.getId();
        Single<CustomerResponse> call = active
                ? customerRepository.update(id, UpdateCustomerRequest.activate(customer))
                : customerRepository.deactivate(id).andThen(customerRepository.get(id));

        disposables.add(call
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        updated -> {
                            message.setValue(FormError.of(active
                                    ? R.string.customer_reactivated : R.string.customer_deactivated));
                            loadFirstPage();
                        },
                        throwable -> message.setValue(
                                FormError.of(NetworkCall.asApiError(throwable).getMessage()))));
    }

    @Override
    protected void onCleared() {
        disposables.clear();
    }
}