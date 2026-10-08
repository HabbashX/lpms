package com.lpms.ui.inventory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.R;
import com.lpms.core.auth.Role;
import com.lpms.core.auth.Session;
import com.lpms.core.error.ApiError;
import com.lpms.core.error.ApiErrorCodes;
import com.lpms.core.network.NetworkCall;
import com.lpms.core.ui.UiState;
import com.lpms.core.util.Money;
import com.lpms.data.dto.CreatePurchaseRequest;
import com.lpms.data.dto.DrugPricingResponse;
import com.lpms.data.dto.DrugResponse;
import com.lpms.data.dto.StockBatchResponse;
import com.lpms.data.repo.AuthRepository;
import com.lpms.data.repo.DrugRepository;
import com.lpms.data.repo.InventoryRepository;
import com.lpms.ui.common.FormError;

import java.math.BigDecimal;
import java.util.Objects;
import java.time.LocalDate;
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
import io.reactivex.rxjava3.disposables.Disposable;

/**
 * Receive stock (ADMIN/PHARMACIST).
 *
 * <p>Records a purchase and creates an <b>immutable batch</b>, so each purchase keeps
 * its own quantity, unit cost, supplier, batch number and expiry. History is never
 * overwritten — that is what makes the weighted-average cost meaningful when the same
 * drug is bought twice at different prices.</p>
 *
 * <p><b>Pricing options:</b> exactly one of None / fixed selling price / profit per
 * unit may be active, which is why the UI is a toggle group. Sending both would be 400
 * {@code INVALID_PRICING}. Before submitting, a preview is fetched from
 * {@code /inventory/drugs/{id}/pricing?profitPerUnit=X} so the cashier sees the average
 * cost and the resulting suggested price.</p>
 */
@HiltViewModel
public final class ReceiveStockViewModel extends ViewModel {

    /** Which pricing effect this purchase has on the drug's default selling price. */
    public enum PricingOption {
        /** Leave the drug's selling price unchanged. */
        NONE,
        /** Set an absolute selling price. */
        FIXED_SELLING_PRICE,
        /** Derive the price from weighted-average cost + profit. */
        PROFIT_PER_UNIT
    }

    /** Live preview card: average cost → suggested selling price. */
    public static final class Preview {
        final BigDecimal averageCost;
        final BigDecimal suggestedSellingPrice;
        final long totalQuantity;

        Preview(@NonNull BigDecimal averageCost, @Nullable BigDecimal suggested,
                long totalQuantity) {
            this.averageCost = averageCost;
            this.suggestedSellingPrice = suggested;
            this.totalQuantity = totalQuantity;
        }
    }

    private final InventoryRepository inventoryRepository;
    private final DrugRepository drugRepository;
    private final AuthRepository authRepository;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<List<DrugResponse>> drugResults =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<DrugResponse> selectedDrug = new MutableLiveData<>();
    private final MutableLiveData<PricingOption> pricingOption =
            new MutableLiveData<>(PricingOption.NONE);
    private final MutableLiveData<Preview> preview = new MutableLiveData<>();
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MutableLiveData<StockBatchResponse> created = new MutableLiveData<>();
    private final MutableLiveData<FormError> message = new MutableLiveData<>();

    private final MutableLiveData<FormError> quantityError = new MutableLiveData<>();
    private final MutableLiveData<FormError> priceError = new MutableLiveData<>();
    private final MutableLiveData<FormError> expirationError = new MutableLiveData<>();

    /**
     * The in-flight preview subscription, held so it can be cancelled.
     *
     * <p>The profit field refreshes the preview as the user types, which without this
     * produced a continuous stream of {@code GET /pricing} calls: responses arrived out of
     * order and each one re-rendered the preview. Cancelling the previous subscription means
     * at most one request is ever outstanding and only the newest answer is applied.</p>
     */
    @Nullable
    private Disposable previewRequest;

    /**
     * The profit value a preview is already being fetched for, so re-entering the same
     * number does not ask again.
     */
    @Nullable
    private BigDecimal previewedProfitPerUnit;

    /** True once a preview has been fetched for the current drug with no profit. */
    private boolean previewedBasePosition;

    @Inject
    public ReceiveStockViewModel(@NonNull InventoryRepository inventoryRepository,
                                 @NonNull DrugRepository drugRepository,
                                 @NonNull AuthRepository authRepository) {
        this.inventoryRepository = inventoryRepository;
        this.drugRepository = drugRepository;
        this.authRepository = authRepository;
    }

    @NonNull
    public LiveData<List<DrugResponse>> drugResults() {
        return drugResults;
    }

    @NonNull
    public LiveData<DrugResponse> selectedDrug() {
        return selectedDrug;
    }

    @NonNull
    public LiveData<PricingOption> pricingOption() {
        return pricingOption;
    }

    @NonNull
    public LiveData<Preview> preview() {
        return preview;
    }

    @NonNull
    public LiveData<Boolean> submitting() {
        return submitting;
    }

    /** Emits the created batch so the screen can confirm and close. */
    @NonNull
    public LiveData<StockBatchResponse> created() {
        return created;
    }

    @NonNull
    public LiveData<FormError> toast() {
        return message;
    }

    @NonNull
    public LiveData<FormError> quantityError() {
        return quantityError;
    }

    @NonNull
    public LiveData<FormError> priceError() {
        return priceError;
    }

    @NonNull
    public LiveData<FormError> expirationError() {
        return expirationError;
    }

    public boolean canManage() {
        Session session = authRepository.sessions().current();
        Role role = session == null ? Role.EMPLOYEE : session.getRole();
        return role.canManageCatalog();
    }

    public void searchDrugs(@Nullable String text) {
        String query = text == null ? "" : text.trim();
        if (query.length() < 2) {
            drugResults.setValue(Collections.emptyList());
            return;
        }
        disposables.add(drugRepository.byNameSearch(query)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(drugResults::setValue,
                        throwable -> drugResults.setValue(Collections.emptyList())));
    }

    /** Selecting a drug loads the current position (quantity + average cost). */
    public void selectDrug(@NonNull DrugResponse drug) {
        selectedDrug.setValue(drug);
        drugResults.setValue(Collections.emptyList());
        // A new drug invalidates the previous what-if, its fetched preview and the drug the
        // cached preview belonged to.
        lastProfitPerUnit = null;
        previewedBasePosition = false;
        previewedProfitPerUnit = null;
        lastPreviewedDrugId = null;
        loadPosition(null);
    }

    public void setPricingOption(@NonNull PricingOption option) {
        pricingOption.setValue(option);
        loadPreviewIfProfit();
    }

    /** Fetches the what-if price so the suggested selling price is visible up front. */
    public void loadPreviewIfProfit() {
        DrugResponse drug = selectedDrug.getValue();
        if (drug == null || drug.getId() == null) {
            return;
        }
        if (pricingOption.getValue() != PricingOption.PROFIT_PER_UNIT) {
            // Still show the average cost so the user sees the starting position.
            loadPosition(null);
            return;
        }
        loadPosition(lastProfitPerUnit);
    }

    /**
     * Called as the profit field changes.
     *
     * <p>The value has to come from here: the ViewModel had no idea what was typed, so every
     * keystroke re-requested the same base position instead of the what-if the user was
     * actually asking about, and the preview never reflected their number.</p>
     *
     * <p>An unparseable or empty value falls back to the base position rather than erroring
     * mid-typing; the field only rejects a bad value on submit.</p>
     */
    public void onProfitChanged(@Nullable String profitText) {
        DrugResponse drug = selectedDrug.getValue();
        if (drug == null || drug.getId() == null) {
            return;
        }
        if (pricingOption.getValue() != PricingOption.PROFIT_PER_UNIT) {
            return;
        }
        BigDecimal parsed = Money.parsePositive(profitText);
        lastProfitPerUnit = parsed;
        loadPosition(parsed);
    }

    @Nullable
    private BigDecimal lastProfitPerUnit;

    private void loadPosition(@Nullable BigDecimal profitPerUnit) {
        DrugResponse drug = selectedDrug.getValue();
        if (drug == null || drug.getId() == null) {
            return;
        }

        // Identical question already answered or on its way: do not ask again. Without this
        // a re-render of the same values produced a request per frame.
        if (profitPerUnit == null && previewedBasePosition) {
            return;
        }
        if (profitPerUnit != null && Objects.equals(profitPerUnit, previewedProfitPerUnit)) {
            return;
        }

        long id = drug.getId();

        // Switching drugs invalidates anything remembered for the previous one.
        if (!Objects.equals(lastPreviewedDrugId, id)) {
            previewedBasePosition = false;
            previewedProfitPerUnit = null;
            lastPreviewedDrugId = id;
        }

        previewedBasePosition = profitPerUnit == null;
        previewedProfitPerUnit = profitPerUnit;

        Single<DrugPricingResponse> call = inventoryRepository.pricing(id,
                profitPerUnit == null
                        ? InventoryRepository.PricingMode.NONE
                        : InventoryRepository.PricingMode.PROFIT_PER_UNIT,
                profitPerUnit, null);

        // Cancel whatever is outstanding: only the newest question is worth answering.
        if (previewRequest != null) {
            previewRequest.dispose();
        }

        previewRequest = call
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        pricing -> {
                            previewRequest = null;
                            preview.setValue(toPreview(pricing));
                        },
                        throwable -> {
                            previewRequest = null;
                            preview.setValue(null);
                        });

        // Also tracked by the ViewModel so it is torn down with it; the local reference
        // above is what loadPosition uses to cancel a superseded request.
        disposables.add(previewRequest);
    }

    @Nullable
    private Long lastPreviewedDrugId;

    @NonNull
    private Preview toPreview(@NonNull DrugPricingResponse pricing) {
        BigDecimal cost = pricing.getWeightedAverageCost() == null
                ? BigDecimal.ZERO : pricing.getWeightedAverageCost();
        return new Preview(cost, pricing.getSuggestedSellingPrice(), pricing.getTotalQuantity());
    }

    public void clearErrors() {
        quantityError.setValue(null);
        priceError.setValue(null);
        expirationError.setValue(null);
        message.setValue(null);
    }

    /**
     * Validates and records the purchase.
     *
     * @param quantityText       required, &gt; 0
     * @param unitPriceText      required, &gt; 0
     * @param expirationText     optional {@code yyyy-MM-dd}; a past date is rejected
     *                          locally with the same wording the server uses
     */
    public void submit(@NonNull String quantityText,
                       @NonNull String unitPriceText,
                       @Nullable String supplier,
                       @Nullable String batchNumber,
                       @Nullable String expirationText,
                       @Nullable String fixedSellingPriceText,
                       @Nullable String profitPerUnitText) {
        if (Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        clearErrors();

        DrugResponse drug = selectedDrug.getValue();
        if (drug == null || drug.getId() == null) {
            message.setValue(FormError.of(R.string.stock_pick_drug_first));
            return;
        }
        if (!drug.isActive()) {
            // Server answers 409 DRUG_INACTIVE; block it before the round trip.
            message.setValue(FormError.of(R.string.stock_drug_inactive));
            return;
        }

        boolean valid = true;

        BigDecimal quantity = Money.parsePositive(quantityText);
        if (quantity == null || quantity.signum() <= 0 || quantity.stripTrailingZeros()
                .scale() > 0) {
            quantityError.setValue(FormError.of(R.string.stock_error_quantity));
            valid = false;
        } else {
            quantityError.setValue(null);
        }

        BigDecimal unitPrice = Money.parsePositive(unitPriceText);
        if (unitPrice == null || unitPrice.signum() <= 0) {
            priceError.setValue(FormError.of(R.string.stock_error_unit_price));
            valid = false;
        } else {
            priceError.setValue(null);
        }

        LocalDate expiration = null;
        if (expirationText != null && !expirationText.trim().isEmpty()) {
            try {
                expiration = LocalDate.parse(expirationText.trim());
            } catch (RuntimeException e) {
                expiration = null;
            }
            if (expiration == null) {
                expirationError.setValue(FormError.of(R.string.stock_error_expiry_format));
                valid = false;
            } else if (expiration.isBefore(LocalDate.now())) {
                // 400 EXPIRATION_IN_PAST on the server.
                expirationError.setValue(FormError.of(R.string.stock_error_expiry_past));
                valid = false;
            } else {
                expirationError.setValue(null);
            }
        }

        PricingOption option = pricingOption.getValue() == null
                ? PricingOption.NONE : pricingOption.getValue();

        // At most one pricing effect may ever be sent.
        BigDecimal sellingPrice = null;
        BigDecimal profitPerUnit = null;
        if (option == PricingOption.FIXED_SELLING_PRICE) {
            sellingPrice = Money.parsePositive(fixedSellingPriceText);
            if (sellingPrice == null) {
                priceError.setValue(FormError.of(R.string.stock_error_selling_price));
                valid = false;
            }
        } else if (option == PricingOption.PROFIT_PER_UNIT) {
            profitPerUnit = Money.parsePositive(profitPerUnitText);
            if (profitPerUnit == null) {
                priceError.setValue(FormError.of(R.string.stock_error_profit));
                valid = false;
            }
            lastProfitPerUnit = profitPerUnit;
        }

        if (!valid || quantity == null || unitPrice == null) {
            return;
        }

        int quantityInt;
        try {
            quantityInt = quantity.intValueExact();
        } catch (ArithmeticException e) {
            quantityError.setValue(FormError.of(R.string.stock_error_quantity));
            return;
        }
        if (quantityInt <= 0) {
            quantityError.setValue(FormError.of(R.string.stock_error_quantity));
            return;
        }

        long drugId = drug.getId();
        CreatePurchaseRequest request;
        if (option == PricingOption.FIXED_SELLING_PRICE && sellingPrice != null) {
            request = CreatePurchaseRequest.withFixedSellingPrice(drugId, quantityInt, unitPrice,
                    supplier, batchNumber, expiration, sellingPrice);
        } else if (option == PricingOption.PROFIT_PER_UNIT && profitPerUnit != null) {
            request = CreatePurchaseRequest.withProfitPerUnit(drugId, quantityInt, unitPrice,
                    supplier, batchNumber, expiration, profitPerUnit);
        } else {
            request = CreatePurchaseRequest.noPricingChange(drugId, quantityInt, unitPrice,
                    supplier, batchNumber, expiration);
        }

        submitting.setValue(true);
        disposables.add(inventoryRepository.purchase(request)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        batch -> {
                            submitting.setValue(false);
                            created.setValue(batch);
                        },
                        throwable -> {
                            submitting.setValue(false);
                            applyServerError(NetworkCall.asApiError(throwable));
                        }));
    }

    private void applyServerError(@NonNull ApiError error) {
        if (ApiErrorCodes.EXPIRATION_IN_PAST.equals(error.getCode())) {
            expirationError.setValue(FormError.of(error.getMessage()));
            return;
        }
        if (ApiErrorCodes.DRUG_INACTIVE.equals(error.getCode())) {
            message.setValue(FormError.of(error.getMessage()));
            return;
        }
        if (ApiErrorCodes.INVALID_PRICING.equals(error.getCode())
                || ApiErrorCodes.INVALID_MARGIN.equals(error.getCode())) {
            priceError.setValue(FormError.of(error.getMessage()));
            return;
        }
        String quantityMessage = error.messageFor("quantity");
        if (quantityMessage != null) {
            quantityError.setValue(FormError.of(quantityMessage));
            return;
        }
        String priceMessage = error.messageFor("unitPurchasePrice");
        if (priceMessage != null) {
            priceError.setValue(FormError.of(priceMessage));
            return;
        }
        message.setValue(FormError.of(error.getMessage()));
    }

    @Override
    protected void onCleared() {
        disposables.clear();
    }
}
