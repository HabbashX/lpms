package com.lpms.ui.customers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.R;
import com.lpms.core.network.NetworkCall;
import com.lpms.core.ui.UiState;
import com.lpms.data.dto.CreateCustomerRequest;
import com.lpms.data.dto.CustomerResponse;
import com.lpms.data.dto.UpdateCustomerRequest;
import com.lpms.data.repo.CustomerRepository;
import com.lpms.ui.common.FormError;

import java.util.Collections;

import javax.inject.Inject;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * Create / edit a customer.
 *
 * <p>Every role may do this; the backend has no role gate on customers. Only the name is
 * required &mdash; many walk-ins have neither phone nor address, and the list search
 * matches name or phone, so forcing a phone would block legitimate entries.</p>
 */
@HiltViewModel
public final class CustomerFormViewModel extends ViewModel {

    private final CustomerRepository customerRepository;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final long customerId;

    private final MutableLiveData<UiState<CustomerResponse>> state = new MutableLiveData<>();
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MutableLiveData<FormError> nameError = new MutableLiveData<>();
    private final MutableLiveData<FormError> message = new MutableLiveData<>();
    private final MutableLiveData<Long> saved = new MutableLiveData<>();

    @Inject
    public CustomerFormViewModel(@NonNull CustomerRepository customerRepository,
                                 @NonNull SavedStateHandle handle) {
        this.customerRepository = customerRepository;
        Long id = handle.get(CustomerListFragment.ARG_CUSTOMER_ID);
        this.customerId = id == null ? 0L : id;
    }

    @NonNull
    public LiveData<UiState<CustomerResponse>> state() {
        return state;
    }

    @NonNull
    public LiveData<Boolean> submitting() {
        return submitting;
    }

    @NonNull
    public LiveData<FormError> nameError() {
        return nameError;
    }

    @NonNull
    public LiveData<FormError> message() {
        return message;
    }

    @NonNull
    public LiveData<Long> saved() {
        return saved;
    }

    public boolean isEditing() {
        return customerId > 0L;
    }

    /** New customer: an empty, ready-to-fill form with no loading spinner. */
    public void startEmpty() {
        state.setValue(UiState.content(new CustomerResponse(
                null, null, null, null, null, Boolean.TRUE, null, null)));
    }

    public void load() {
        if (!isEditing()) {
            startEmpty();
            return;
        }
        state.setValue(UiState.loading());
        disposables.add(customerRepository.get(customerId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        customer -> state.setValue(UiState.content(customer)),
                        throwable -> state.setValue(
                                UiState.error(NetworkCall.asApiError(throwable)))));
    }

    public void save(@Nullable String name,
                     @Nullable String phone,
                     @Nullable String address,
                     @Nullable String notes,
                     boolean active) {

        if (Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        nameError.setValue(null);
        message.setValue(null);

        if (name == null || name.trim().isEmpty()) {
            nameError.setValue(FormError.of(R.string.customer_error_name_required));
            return;
        }

        submitting.setValue(true);
        String trimmedName = name.trim();

        Single<CustomerResponse> call = isEditing()
                ? customerRepository.update(customerId, new UpdateCustomerRequest(
                trimmedName, trimToNull(phone), trimToNull(address), trimToNull(notes), active))
                : customerRepository.create(new CreateCustomerRequest(
                trimmedName, trimToNull(phone), trimToNull(address), trimToNull(notes)));

        disposables.add(call
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        savedCustomer -> {
                            submitting.setValue(false);
                            saved.setValue(savedCustomer.getId() == null
                                    ? customerId : savedCustomer.getId());
                        },
                        throwable -> {
                            submitting.setValue(false);
                            onSaveFailed(throwable);
                        }));
    }

    private void onSaveFailed(@NonNull Throwable throwable) {
        com.lpms.core.error.ApiError error = NetworkCall.asApiError(throwable);
        // 400 VALIDATION_FAILED names the field it rejected; put it on that field.
        String fieldMessage = error.messageFor("name");
        if (fieldMessage != null) {
            nameError.setValue(FormError.of(fieldMessage));
            return;
        }
        message.setValue(FormError.of(error.getMessage()));
    }

    public void clearErrors() {
        nameError.setValue(null);
        message.setValue(null);
    }

    @Nullable
    private static String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    protected void onCleared() {
        disposables.clear();
    }
}