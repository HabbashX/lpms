package com.lpms.ui.sales;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.core.auth.Role;
import com.lpms.core.auth.Session;
import com.lpms.core.network.NetworkCall;
import com.lpms.core.ui.UiState;
import com.lpms.data.dto.CreateRefundRequest;
import com.lpms.data.dto.RefundItemRequest;
import com.lpms.data.dto.RefundResponse;
import com.lpms.data.dto.SaleResponse;
import com.lpms.data.repo.AuthRepository;
import com.lpms.data.repo.SalesRepository;
import com.lpms.ui.common.FormError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * Sale receipt / detail.
 *
 * <p><b>Cost and profit are returned to every role</b>, so {@link #canSeeProfit()} gates
 * them here: EMPLOYEE sees subtotal, discount, total, paid and due only.</p>
 *
 * <p>Refund (ADMIN/PHARMACIST): per-line quantities capped at
 * {@code quantity − refundedQuantity}. An empty selection is sent as a request with no
 * {@code items}, which the server treats as "refund everything still refundable".
 * There is no refunds-list endpoint, so the sale is re-read after every refund.</p>
 */
@HiltViewModel
public final class SaleDetailViewModel extends ViewModel {

    private final SalesRepository salesRepository;
    private final AuthRepository authRepository;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final long saleId;

    private final MutableLiveData<UiState<SaleResponse>> state = new MutableLiveData<>();
    private final MutableLiveData<Boolean> refunding = new MutableLiveData<>(false);
    private final MutableLiveData<FormError> message = new MutableLiveData<>();
    private final MutableLiveData<RefundResponse> refunded = new MutableLiveData<>();

    /** saleItemId → quantity the cashier stepped to; absent = not selected for refund. */
    private final Map<Long, Integer> refundSelection = new LinkedHashMap<>();

    @Inject
    public SaleDetailViewModel(@NonNull SalesRepository salesRepository,
                               @NonNull AuthRepository authRepository,
                               @NonNull SavedStateHandle handle) {
        this.salesRepository = salesRepository;
        this.authRepository = authRepository;
        Long id = handle.get(SaleDetailFragment.ARG_SALE_ID);
        this.saleId = id == null ? 0L : id;
    }

    @NonNull
    public LiveData<UiState<SaleResponse>> state() {
        return state;
    }

    @NonNull
    public LiveData<Boolean> refunding() {
        return refunding;
    }

    @NonNull
    public LiveData<FormError> toast() {
        return message;
    }

    @NonNull
    public LiveData<RefundResponse> refunded() {
        return refunded;
    }

    /** Only ADMIN/PHARMACIST may refund a sale. */
    public boolean canRefund() {
        Session session = authRepository.sessions().current();
        Role role = session == null ? Role.EMPLOYEE : session.getRole();
        return role.canRefund();
    }

    public boolean canSeeProfit() {
        Session session = authRepository.sessions().current();
        Role role = session == null ? Role.EMPLOYEE : session.getRole();
        return role.canSeeProfit();
    }

    public void load() {
        state.setValue(UiState.loading());
        disposables.add(salesRepository.get(saleId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        sale -> state.setValue(UiState.content(sale)),
                        throwable -> state.setValue(
                                UiState.error(NetworkCall.asApiError(throwable)))));
    }

    // ------------------------------------------------------------------- refund

    public void toggleRefundLine(long saleItemId, int refundableQuantity) {
        Integer current = refundSelection.get(saleItemId);
        if (current == null) {
            refundSelection.put(saleItemId, refundableQuantity);
            return;
        }
        refundSelection.remove(saleItemId);
    }

    public void setRefundQuantity(long saleItemId, int quantity) {
        if (quantity <= 0) {
            refundSelection.remove(saleItemId);
        } else {
            refundSelection.put(saleItemId, quantity);
        }
    }

    public int refundQuantityFor(long saleItemId) {
        Integer value = refundSelection.get(saleItemId);
        return value == null ? 0 : value;
    }

    public boolean hasSelection() {
        return !refundSelection.isEmpty();
    }

    public void clearSelection() {
        refundSelection.clear();
    }

    /** Submits the refund, then re-reads the sale for its new status and totals. */
    public void submitRefund(@Nullable String reason) {
        if (Boolean.TRUE.equals(refunding.getValue())) {
            return;
        }
        List<RefundItemRequest> items = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : refundSelection.entrySet()) {
            items.add(new RefundItemRequest(entry.getKey(), entry.getValue()));
        }
        CreateRefundRequest request = items.isEmpty()
                ? CreateRefundRequest.refundEverything(reason)
                : CreateRefundRequest.ofLines(items, reason);

        refunding.setValue(true);
        disposables.add(salesRepository.refund(saleId, request)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        refund -> {
                            refunding.setValue(false);
                            refundSelection.clear();
                            refunded.setValue(refund);
                            load();
                        },
                        throwable -> {
                            refunding.setValue(false);
                            message.setValue(FormError.of(
                                    NetworkCall.asApiError(throwable).getMessage()));
                        }));
    }

    /** Zero or an empty selection means "everything still refundable". */
    @NonNull
    public List<RefundItemRequest> currentSelection() {
        if (refundSelection.isEmpty()) {
            return Collections.emptyList();
        }
        List<RefundItemRequest> items = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : refundSelection.entrySet()) {
            items.add(new RefundItemRequest(entry.getKey(), entry.getValue()));
        }
        return items;
    }

    @Override
    protected void onCleared() {
        disposables.clear();
    }
}