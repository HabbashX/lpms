package com.lpms.ui.drugs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.R;
import com.lpms.core.error.ApiError;
import com.lpms.core.network.NetworkCall;
import com.lpms.core.ui.UiState;
import com.lpms.core.util.Money;
import com.lpms.data.dto.CategoryResponse;
import com.lpms.data.dto.CreateDrugRequest;
import com.lpms.data.dto.DosageForm;
import com.lpms.data.dto.DrugResponse;
import com.lpms.data.dto.UpdateDrugRequest;
import com.lpms.data.repo.CategoryRepository;
import com.lpms.data.repo.DrugRepository;
import com.lpms.ui.common.FormError;

import java.math.BigDecimal;
import java.util.Collections;
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
 * Create / edit form for a drug.
 *
 * <p>Validation mirrors the server so the user sees problems before the round trip:
 * name required (≤150), barcode ≤50, selling price &gt; 0 when supplied, minimum stock
 * level ≥ 0. Anything the server rejects anyway (duplicate barcode → 409
 * {@code BARCODE_ALREADY_EXISTS}, unknown categoryId → 404) is surfaced by message.</p>
 *
 * <p>{@code categoryId} from the cached category list is always sent; the legacy
 * free-text {@code category} field is never populated.</p>
 */
@HiltViewModel
public final class DrugFormViewModel extends ViewModel {

    /** Form fields, held as one value so the Fragment only reads. */
    public static final class FormState {

        public final boolean editing;
        public final String name;
        public final String genericName;
        public final String barcode;
        public final String manufacturer;
        public final Long categoryId;
        public final DosageForm dosageForm;
        public final String strength;
        public final String unit;
        public final BigDecimal sellingPrice;
        public final String description;
        public final Integer minimumStockLevel;
        public final boolean active;
        public final int currentQuantity;

        FormState(boolean editing,
                  @Nullable String name,
                  @Nullable String genericName,
                  @Nullable String barcode,
                  @Nullable String manufacturer,
                  @Nullable Long categoryId,
                  @Nullable DosageForm dosageForm,
                  @Nullable String strength,
                  @Nullable String unit,
                  @Nullable BigDecimal sellingPrice,
                  @Nullable String description,
                  @Nullable Integer minimumStockLevel,
                  boolean active,
                  int currentQuantity) {
            this.editing = editing;
            this.name = name;
            this.genericName = genericName;
            this.barcode = barcode;
            this.manufacturer = manufacturer;
            this.categoryId = categoryId;
            this.dosageForm = dosageForm;
            this.strength = strength;
            this.unit = unit;
            this.sellingPrice = sellingPrice;
            this.description = description;
            this.minimumStockLevel = minimumStockLevel;
            this.active = active;
            this.currentQuantity = currentQuantity;
        }

        static FormState empty() {
            return new FormState(false, "", "", "", "", null, DosageForm.OTHER, "", "",
                    null, "", 0, true, 0);
        }
    }

    private final DrugRepository drugRepository;
    private final CategoryRepository categoryRepository;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final long drugId;

    private final MutableLiveData<UiState<FormState>> state = new MutableLiveData<>();
    private final MutableLiveData<List<CategoryResponse>> categories =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MutableLiveData<FormError> nameError = new MutableLiveData<>();
    private final MutableLiveData<FormError> barcodeError = new MutableLiveData<>();
    private final MutableLiveData<FormError> priceError = new MutableLiveData<>();
    private final MutableLiveData<FormError> minimumStockError = new MutableLiveData<>();
    private final MutableLiveData<FormError> message = new MutableLiveData<>();
    private final MutableLiveData<Long> savedDrugId = new MutableLiveData<>();

    @Inject
    public DrugFormViewModel(@NonNull DrugRepository drugRepository,
                             @NonNull CategoryRepository categoryRepository,
                             @NonNull SavedStateHandle handle) {
        this.drugRepository = drugRepository;
        this.categoryRepository = categoryRepository;
        Long id = handle.get(DrugListFragment.ARG_DRUG_ID);
        this.drugId = id == null ? 0L : id;
    }

    @NonNull
    public LiveData<UiState<FormState>> state() {
        return state;
    }

    @NonNull
    public LiveData<List<CategoryResponse>> categories() {
        return categories;
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
    public LiveData<FormError> barcodeError() {
        return barcodeError;
    }

    @NonNull
    public LiveData<FormError> priceError() {
        return priceError;
    }

    @NonNull
    public LiveData<FormError> minimumStockError() {
        return minimumStockError;
    }

    @NonNull
    public LiveData<FormError> message() {
        return message;
    }

    /** Emits the saved drug id so the screen can pop with a result. */
    @NonNull
    public LiveData<Long> saved() {
        return savedDrugId;
    }

    public boolean isEditing() {
        return drugId > 0L;
    }

    /** Loads the existing drug when editing; categories either way. */
    public void load() {
        loadCategories();
        if (!isEditing()) {
            state.setValue(UiState.content(FormState.empty()));
            return;
        }
        state.setValue(UiState.loading());
        disposables.add(drugRepository.get(drugId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::applyLoaded,
                        throwable -> state.setValue(
                                UiState.error(NetworkCall.asApiError(throwable)))));
    }

    private void loadCategories() {
        disposables.add(categoryRepository.listOrEmpty()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(categories::setValue,
                        throwable -> categories.setValue(Collections.emptyList())));
    }

    private void applyLoaded(@NonNull DrugResponse drug) {
        state.setValue(UiState.content(new FormState(
                true,
                drug.getName(),
                drug.getGenericName(),
                drug.getBarcode(),
                drug.getManufacturer(),
                drug.getCategoryId(),
                DosageForm.fromNullable(drug.getDosageForm()),
                drug.getStrength(),
                drug.getUnit(),
                drug.getSellingPrice(),
                drug.getDescription(),
                drug.getMinimumStockLevel(),
                drug.isActive(),
                drug.getCurrentQuantity())));
    }

    /**
     * Validates and saves. On success the request is a POST (create) or PUT (edit); the
     * server owns barcode uniqueness and category validity.
     */
    public void save(@NonNull String name,
                     @Nullable String genericName,
                     @Nullable String barcode,
                     @Nullable String manufacturer,
                     @Nullable Long categoryId,
                     @Nullable DosageForm dosageForm,
                     @Nullable String strength,
                     @Nullable String unit,
                     @Nullable String sellingPriceText,
                     @Nullable String description,
                     @Nullable String minimumStockText,
                     boolean active) {

        if (Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        clearErrors();

        boolean valid = true;
        if (name == null || name.trim().isEmpty()) {
            nameError.setValue(FormError.of(R.string.drug_error_name_required));
            valid = false;
        } else if (name.trim().length() > 150) {
            nameError.setValue(FormError.of(R.string.drug_error_name_too_long));
            valid = false;
        }
        if (barcode != null && !barcode.trim().isEmpty() && barcode.trim().length() > 50) {
            barcodeError.setValue(FormError.of(R.string.drug_error_barcode_too_long));
            valid = false;
        }

        BigDecimal sellingPrice = null;
        if (sellingPriceText != null && !sellingPriceText.trim().isEmpty()) {
            sellingPrice = Money.parsePositive(sellingPriceText);
            if (sellingPrice == null) {
                priceError.setValue(FormError.of(R.string.drug_error_price_invalid));
                valid = false;
            }
        }

        Integer minimumStock = null;
        if (minimumStockText != null && !minimumStockText.trim().isEmpty()) {
            BigDecimal parsed = Money.parse(minimumStockText);
            if (parsed == null || parsed.signum() < 0 || parsed.scale() > 0) {
                minimumStockError.setValue(FormError.of(R.string.drug_error_min_stock_invalid));
                valid = false;
            } else {
                minimumStock = parsed.intValue();
            }
        }

        if (!valid) {
            return;
        }

        submitting.setValue(true);
        String trimmedName = name.trim();

        if (isEditing()) {
            UpdateDrugRequest request = new UpdateDrugRequest(
                    trimmedName,
                    trimToNull(genericName),
                    trimToNull(barcode),
                    trimToNull(manufacturer),
                    categoryId,
                    dosageForm == null ? null : dosageForm.wireValue(),
                    trimToNull(strength),
                    trimToNull(unit),
                    sellingPrice,
                    trimToNull(description),
                    minimumStock,
                    active);
            disposables.add(drugRepository.update(drugId, request)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            saved -> {
                                submitting.setValue(false);
                                categoryRepository.invalidate();
                                savedDrugId.setValue(drugId);
                            },
                            throwable -> onSaveFailed(throwable)));
            return;
        }

        CreateDrugRequest request = new CreateDrugRequest(
                trimmedName,
                trimToNull(genericName),
                trimToNull(barcode),
                trimToNull(manufacturer),
                categoryId,
                dosageForm == null ? null : dosageForm.wireValue(),
                trimToNull(strength),
                trimToNull(unit),
                sellingPrice,
                trimToNull(description),
                minimumStock);
        disposables.add(drugRepository.create(request)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        saved -> {
                            submitting.setValue(false);
                            categoryRepository.invalidate();
                            savedDrugId.setValue(saved.getId() == null ? 0L : saved.getId());
                        },
                        throwable -> onSaveFailed(throwable)));
    }

    private void onSaveFailed(@NonNull Throwable throwable) {
        submitting.setValue(false);
        ApiError error = NetworkCall.asApiError(throwable);

        // 409 BARCODE_ALREADY_EXISTS and 400 VALIDATION_FAILED belong on their field.
        String nameMessage = error.messageFor("name");
        if (nameMessage != null) {
            nameError.setValue(FormError.of(nameMessage));
            return;
        }
        String barcodeMessage = error.messageFor("barcode");
        if (barcodeMessage != null) {
            barcodeError.setValue(FormError.of(barcodeMessage));
            return;
        }
        String priceMessage = error.messageFor("sellingPrice");
        if (priceMessage != null) {
            priceError.setValue(FormError.of(priceMessage));
            return;
        }
        String minimumMessage = error.messageFor("minimumStockLevel");
        if (minimumMessage != null) {
            minimumStockError.setValue(FormError.of(minimumMessage));
            return;
        }
        message.setValue(FormError.of(error.getMessage()));
    }

    public void clearErrors() {
        nameError.setValue(null);
        barcodeError.setValue(null);
        priceError.setValue(null);
        minimumStockError.setValue(null);
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