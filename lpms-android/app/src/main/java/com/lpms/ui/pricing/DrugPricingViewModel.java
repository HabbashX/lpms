package com.lpms.ui.pricing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.R;
import com.lpms.core.auth.Role;
import com.lpms.core.auth.Session;
import com.lpms.core.network.NetworkCall;
import com.lpms.core.ui.UiState;
import com.lpms.core.util.Money;
import com.lpms.data.dto.DrugPricingResponse;
import com.lpms.data.dto.UpdateDrugRequest;
import com.lpms.data.repo.AuthRepository;
import com.lpms.data.repo.DrugRepository;
import com.lpms.data.repo.InventoryRepository;
import com.lpms.ui.common.FormError;

import java.math.BigDecimal;

import javax.inject.Inject;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * Recommended pricing for one drug (ADMIN/PHARMACIST — the endpoint shows cost and is
 * role-restricted server-side).
 *
 * <p>Flow: load current position → user picks <b>Profit per unit</b> or <b>Margin %</b>
 * and types a value → the same endpoint is called with exactly that one parameter →
 * {@code suggestedSellingPrice} is shown → "Apply" pushes it onto the drug with
 * {@code PUT /drugs/{id}}.</p>
 *
 * <p>Never sends both parameters: that is 400 {@code INVALID_PRICING}. A margin must be
 * 0 ≤ m &lt; 100, otherwise 400 {@code INVALID_MARGIN}; the check is duplicated locally so
 * the user is not told off by a round trip.</p>
 */
@HiltViewModel
public final class DrugPricingViewModel extends ViewModel {

    private final InventoryRepository inventoryRepository;
    private final DrugRepository drugRepository;
    private final AuthRepository authRepository;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final long drugId;

    private final MutableLiveData<UiState<DrugPricingResponse>> state = new MutableLiveData<>();
    private final MutableLiveData<InventoryRepository.PricingMode> mode =
            new MutableLiveData<>(InventoryRepository.PricingMode.NONE);
    private final MutableLiveData<Boolean> applying = new MutableLiveData<>(false);
    private final MutableLiveData<FormError> message = new MutableLiveData<>();
    private final MutableLiveData<Boolean> applied = new MutableLiveData<>();

    @Inject
    public DrugPricingViewModel(@NonNull InventoryRepository inventoryRepository,
                                @NonNull DrugRepository drugRepository,
                                @NonNull AuthRepository authRepository,
                                @NonNull SavedStateHandle handle) {
        this.inventoryRepository = inventoryRepository;
        this.drugRepository = drugRepository;
        this.authRepository = authRepository;
        Long id = handle.get(DrugPricingFragment.ARG_DRUG_ID);
        this.drugId = id == null ? 0L : id;
    }

    @NonNull
    public LiveData<UiState<DrugPricingResponse>> state() {
        return state;
    }

    @NonNull
    public LiveData<InventoryRepository.PricingMode> mode() {
        return mode;
    }

    @NonNull
    public LiveData<Boolean> applying() {
        return applying;
    }

    @NonNull
    public LiveData<FormError> toast() {
        return message;
    }

    /** Emits true once the new selling price has been saved. */
    @NonNull
    public LiveData<Boolean> applied() {
        return applied;
    }

    public boolean canSeeCost() {
        Session session = authRepository.sessions().current();
        Role role = session == null ? Role.EMPLOYEE : session.getRole();
        return role.canSeeProfit();
    }

    public void load() {
        fetch(null, null);
    }

    /** Recomputes the suggestion for the current mode. */
    public void refreshSuggestion() {
        fetch(pendingProfit, pendingMargin);
    }

    @Nullable
    private BigDecimal pendingProfit;
    @Nullable
    private BigDecimal pendingMargin;

    /**
     * @param profitPerUnit ignored unless mode is PROFIT_PER_UNIT
     * @param marginPercent ignored unless mode is MARGIN_PERCENT
     */
    public void selectMode(@NonNull InventoryRepository.PricingMode newMode,
                           @Nullable String profitPerUnitText,
                           @Nullable String marginPercentText) {
        mode.setValue(newMode);
        pendingProfit = null;
        pendingMargin = null;

        if (newMode == InventoryRepository.PricingMode.PROFIT_PER_UNIT) {
            BigDecimal profit = Money.parsePositive(profitPerUnitText);
            if (profit == null) {
                message.setValue(FormError.of(R.string.pricing_error_profit_required));
                return;
            }
            pendingProfit = profit;
        } else if (newMode == InventoryRepository.PricingMode.MARGIN_PERCENT) {
            BigDecimal margin = Money.parsePositive(marginPercentText);
            if (margin == null) {
                message.setValue(FormError.of(R.string.pricing_error_margin_required));
                return;
            }
            if (margin.signum() < 0 || margin.compareTo(BigDecimal.valueOf(100)) >= 0) {
                // Server contract: 0 <= margin < 100, else 400 INVALID_MARGIN.
                message.setValue(FormError.of(R.string.pricing_error_margin_range));
                return;
            }
            pendingMargin = margin;
        } else {
            // NONE: plain position report, no what-if parameter.
            fetch(null, null);
            return;
        }
        fetch(pendingProfit, pendingMargin);
    }

    private void fetch(@Nullable BigDecimal profit, @Nullable BigDecimal margin) {
        state.setValue(UiState.loading());
        disposables.add(inventoryRepository.pricing(drugId, resolveMode(profit, margin),
                        profit, margin)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        pricing -> state.setValue(UiState.content(pricing)),
                        throwable -> state.setValue(
                                UiState.error(NetworkCall.asApiError(throwable)))));
    }

    @NonNull
    private InventoryRepository.PricingMode resolveMode(@Nullable BigDecimal profit,
                                                       @Nullable BigDecimal margin) {
        // Exactly one parameter may ever be sent.
        if (profit != null) {
            return InventoryRepository.PricingMode.PROFIT_PER_UNIT;
        }
        if (margin != null) {
            return InventoryRepository.PricingMode.MARGIN_PERCENT;
        }
        InventoryRepository.PricingMode current = mode.getValue();
        return current == null ? InventoryRepository.PricingMode.NONE : current;
    }

    /** Persists {@code suggestedSellingPrice} as the drug's default selling price. */
    public void applySuggestedPrice() {
        if (Boolean.TRUE.equals(applying.getValue())) {
            return;
        }
        DrugPricingResponse pricing = state.getValue() == null ? null
                : state.getValue().valueOrNull();
        BigDecimal suggested = pricing == null ? null : pricing.getSuggestedSellingPrice();
        if (suggested == null) {
            message.setValue(FormError.of(R.string.pricing_error_no_suggestion));
            return;
        }

        applying.setValue(true);
        disposables.add(drugRepository.get(drugId)
                .flatMap(drug -> drugRepository.update(drug.getId(),
                        UpdateDrugRequest.withSellingPrice(drug, suggested)))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        updated -> {
                            applying.setValue(false);
                            message.setValue(FormError.of(R.string.pricing_applied));
                            applied.setValue(true);
                            load();
                        },
                        throwable -> {
                            applying.setValue(false);
                            message.setValue(FormError.of(
                                    NetworkCall.asApiError(throwable).getMessage()));
                        }));
    }

    /** Convenience used by the screen's preview line before the round trip finishes. */
    @Nullable
    public static BigDecimal localSuggestion(@NonNull DrugPricingResponse pricing,
                                             @NonNull InventoryRepository.PricingMode mode,
                                             @Nullable BigDecimal input) {
        if (input == null) {
            return null;
        }
        BigDecimal cost = pricing.getWeightedAverageCost();
        if (mode == InventoryRepository.PricingMode.PROFIT_PER_UNIT) {
            return DrugPricingResponse.sellingPriceForProfit(cost, input);
        }
        if (mode == InventoryRepository.PricingMode.MARGIN_PERCENT) {
            return DrugPricingResponse.sellingPriceForMargin(cost, input);
        }
        return null;
    }

    /** True when the drug has no stock, so cost/profit figures are meaningless. */
    public static boolean hasNoStock(@Nullable DrugPricingResponse pricing) {
        return pricing == null || !pricing.hasStock();
    }

    /** Never-null drug id accessor for the Fragment. */
    public long drugId() {
        return drugId;
    }


    @Override
    protected void onCleared() {
        disposables.clear();
    }
}