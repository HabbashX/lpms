package com.lpms.ui.customers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.R;
import com.lpms.core.network.NetworkCall;
import com.lpms.core.ui.UiState;
import com.lpms.core.util.Money;
import com.lpms.data.dto.CreateAdjustmentRequest;
import com.lpms.data.dto.CreatePaymentRequest;
import com.lpms.data.dto.CustomerAccountResponse;
import com.lpms.data.dto.CustomerResponse;
import com.lpms.data.dto.PaymentMethod;
import com.lpms.data.dto.TransactionDirection;
import com.lpms.data.repo.CustomerRepository;
import com.lpms.ui.common.FormError;

import java.math.BigDecimal;
import java.util.List;

import javax.inject.Inject;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * Customer detail: identity plus the account statement.
 *
 * <p>The statement arrives as one response ({@code GET /customers/{id}/account}) with a
 * page of transactions inside it, rather than as its own endpoint, so "load more" re-reads
 * the account with a larger page index instead of calling something that does not exist.</p>
 */
@HiltViewModel
public final class CustomerDetailViewModel extends ViewModel {

    /** Matches the server's own default. */
    private static final int TRANSACTION_PAGE_SIZE = 20;
    private static final int TRANSACTION_PAGE_GROWTH = 20;

    private final CustomerRepository customerRepository;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final long customerId;

    private final MutableLiveData<UiState<CustomerAccountResponse>> state =
            new MutableLiveData<>();
    private final MutableLiveData<Boolean> refreshing = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<FormError> paymentAmountError = new MutableLiveData<>();
    private final MutableLiveData<FormError> paymentMethodError = new MutableLiveData<>();
    private final MutableLiveData<FormError> adjustmentAmountError = new MutableLiveData<>();
    private final MutableLiveData<FormError> adjustmentDescriptionError =
            new MutableLiveData<>();
    private final MutableLiveData<FormError> message = new MutableLiveData<>();
    private final MutableLiveData<Long> finished = new MutableLiveData<>();

    /** Transaction rows currently loaded; the view grows it, never shrinks it. */
    private int transactionLimit = TRANSACTION_PAGE_SIZE;
    private boolean hasMoreTransactions = true;

    @Inject
    public CustomerDetailViewModel(@NonNull CustomerRepository customerRepository,
                                   @NonNull SavedStateHandle handle) {
        this.customerRepository = customerRepository;
        Long id = handle.get(CustomerListFragment.ARG_CUSTOMER_ID);
        this.customerId = id == null ? 0L : id;
    }

    @NonNull
    public LiveData<UiState<CustomerAccountResponse>> state() {
        return state;
    }

    @NonNull
    public LiveData<Boolean> refreshing() {
        return refreshing;
    }

    @NonNull
    public LiveData<Boolean> busy() {
        return busy;
    }

    @NonNull
    public LiveData<FormError> paymentAmountError() {
        return paymentAmountError;
    }

    @NonNull
    public LiveData<FormError> paymentMethodError() {
        return paymentMethodError;
    }

    @NonNull
    public LiveData<FormError> adjustmentAmountError() {
        return adjustmentAmountError;
    }

    @NonNull
    public LiveData<FormError> adjustmentDescriptionError() {
        return adjustmentDescriptionError;
    }

    @NonNull
    public LiveData<FormError> message() {
        return message;
    }

    /** Emits after a payment or adjustment so the screen can pop. */
    @NonNull
    public LiveData<Long> finished() {
        return finished;
    }

    public void load() {
        state.setValue(UiState.loading());
        fetch(false);
    }

    public void refresh() {
        refreshing.setValue(true);
        fetch(true);
    }

    /**
     * Grows the statement window by one step. The server returns the totals on every read,
     * so this is a re-read, not a separate page request.
     */
    public void loadMoreTransactions() {
        if (!hasMoreTransactions) {
            return;
        }
        busy.setValue(true);
        transactionLimit += TRANSACTION_PAGE_GROWTH;
        fetch(true);
    }

    private void fetch(boolean keepExistingRows) {
        if (!keepExistingRows) {
            transactionLimit = TRANSACTION_PAGE_SIZE;
        }
        disposables.add(customerRepository.account(customerId, 0, transactionLimit)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        account -> {
                            refreshing.setValue(false);
                            busy.setValue(false);
                            hasMoreTransactions = account.getTransactions() != null
                                    && account.getTransactions().getContent().size()
                                    >= transactionLimit;
                            state.setValue(UiState.content(account));
                        },
                        throwable -> {
                            refreshing.setValue(false);
                            busy.setValue(false);
                            state.setValue(UiState.error(NetworkCall.asApiError(throwable)));
                        }));
    }

    /**
     * Records a payment against the debt.
     *
     * <p>No automatic retry: the server has no idempotency key here, so a silent second
     * attempt could credit the account twice.</p>
     */
    public void recordPayment(@Nullable String amountText, @Nullable String paymentMethodWire,
                              @Nullable String notes) {
        if (Boolean.TRUE.equals(busy.getValue())) {
            return;
        }
        clearPaymentErrors();

        BigDecimal amount = Money.parsePositive(amountText);
        if (amount == null) {
            paymentAmountError.setValue(FormError.of(R.string.customer_error_amount_required));
            return;
        }
        PaymentMethod method = PaymentMethod.fromNullable(paymentMethodWire);
        if (method == null) {
            paymentMethodError.setValue(FormError.of(R.string.customer_error_payment_method));
            return;
        }

        busy.setValue(true);
        disposables.add(customerRepository.recordPayment(customerId,
                        new CreatePaymentRequest(amount, method, notes))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        payment -> {
                            busy.setValue(false);
                            message.setValue(FormError.of(R.string.customer_payment_recorded));
                            finished.setValue(customerId);
                        },
                        throwable -> {
                            busy.setValue(false);
                            message.setValue(FormError.of(
                                    NetworkCall.asApiError(throwable).getMessage()));
                        }));
    }

    /** Manual ledger correction; the server audits both directions. */
    public void adjust(@Nullable String amountText, boolean credit,
                       @Nullable String description) {
        if (Boolean.TRUE.equals(busy.getValue())) {
            return;
        }
        clearAdjustmentErrors();

        BigDecimal amount = Money.parsePositive(amountText);
        if (amount == null) {
            adjustmentAmountError.setValue(FormError.of(R.string.customer_error_amount_required));
            return;
        }
        String trimmedDescription = description == null ? "" : description.trim();
        if (trimmedDescription.isEmpty()) {
            // An unexplained balance correction is not auditable.
            adjustmentDescriptionError.setValue(
                    FormError.of(R.string.customer_error_adjustment_reason));
            return;
        }

        busy.setValue(true);
        disposables.add(customerRepository.adjust(customerId, new CreateAdjustmentRequest(
                        amount,
                        (credit ? TransactionDirection.CREDIT : TransactionDirection.DEBIT),
                        trimmedDescription))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        transaction -> {
                            busy.setValue(false);
                            message.setValue(FormError.of(R.string.customer_adjusted));
                            finished.setValue(customerId);
                        },
                        throwable -> {
                            busy.setValue(false);
                            message.setValue(FormError.of(
                                    NetworkCall.asApiError(throwable).getMessage()));
                        }));
    }

    private void clearPaymentErrors() {
        paymentAmountError.setValue(null);
        paymentMethodError.setValue(null);
    }

    private void clearAdjustmentErrors() {
        adjustmentAmountError.setValue(null);
        adjustmentDescriptionError.setValue(null);
    }

    /** Current debt, or null while loading / on error. */
    @Nullable
    public BigDecimal currentDebt() {
        CustomerAccountResponse account = accountOrNull();
        return account == null ? null : account.getCurrentDebt();
    }

    @Nullable
    public CustomerResponse customer() {
        CustomerAccountResponse account = accountOrNull();
        return account == null ? null : account.getCustomer();
    }

    @Nullable
    private CustomerAccountResponse accountOrNull() {
        UiState<CustomerAccountResponse> current = state.getValue();
        return current != null && current.isContent() ? current.valueOrNull() : null;
    }

    @Nullable
    public List<com.lpms.data.dto.TransactionResponse> transactions() {
        CustomerAccountResponse account = accountOrNull();
        if (account == null || account.getTransactions() == null) {
            return null;
        }
        return account.getTransactions().getContent();
    }

    public boolean hasMoreTransactions() {
        return hasMoreTransactions;
    }

    @Override
    protected void onCleared() {
        disposables.clear();
    }
}