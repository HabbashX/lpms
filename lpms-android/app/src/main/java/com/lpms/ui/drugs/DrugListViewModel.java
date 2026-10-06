package com.lpms.ui.drugs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.R;
import com.lpms.core.auth.Role;
import com.lpms.core.auth.Session;
import com.lpms.core.auth.SessionManager;
import com.lpms.core.error.ApiErrorMapper;
import com.lpms.core.network.NetworkCall;
import com.lpms.core.network.PagedAccumulator;
import com.lpms.data.dto.CategoryResponse;
import com.lpms.data.dto.DrugResponse;
import com.lpms.data.dto.UpdateDrugRequest;
import com.lpms.data.repo.AuthRepository;
import com.lpms.data.repo.CategoryRepository;
import com.lpms.data.repo.DrugRepository;
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
 * Drug list: search, filters, sort and paging.
 *
 * <p>Paging uses {@link PagedAccumulator} on top of a Paging 3
 * {@link androidx.paging.rxjava3.RxPagingSource}; a filter change replaces the
 * accumulator and restarts at page 0. No RemoteMediator and no local cache — rows always
 * come from the server.</p>
 */
@HiltViewModel
public final class DrugListViewModel extends ViewModel {

    private final DrugRepository drugRepository;
    private final CategoryRepository categoryRepository;
    private final ApiErrorMapper errorMapper;
    private final SessionManager sessionManager;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<DrugRepository.Filter> filter =
            new MutableLiveData<>(DrugRepository.Filter.none());
    private final MutableLiveData<List<DrugResponse>> items =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<PagingUiState> pagingState =
            new MutableLiveData<>(PagingUiState.initial());
    private final MutableLiveData<Boolean> refreshing = new MutableLiveData<>(false);
    private final MutableLiveData<List<CategoryResponse>> categories =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<FormError> message = new MutableLiveData<>();

    @Nullable
    private PagedAccumulator<DrugResponse> accumulator;
    private boolean requestInFlight;

    @Inject
    public DrugListViewModel(@NonNull DrugRepository drugRepository,
                             @NonNull CategoryRepository categoryRepository,
                             @NonNull ApiErrorMapper errorMapper,
                             @NonNull AuthRepository authRepository) {
        this.drugRepository = drugRepository;
        this.categoryRepository = categoryRepository;
        this.errorMapper = errorMapper;
        this.sessionManager = authRepository.sessions();
    }

    @NonNull
    public LiveData<List<DrugResponse>> items() {
        return items;
    }

    @NonNull
    public LiveData<PagingUiState> pagingState() {
        return pagingState;
    }

    @NonNull
    public LiveData<DrugRepository.Filter> filter() {
        return filter;
    }

    @NonNull
    public LiveData<Boolean> refreshing() {
        return refreshing;
    }

    /** Cached categories for the filter chip and the drug-form dropdown. */
    @NonNull
    public LiveData<List<CategoryResponse>> categories() {
        return categories;
    }

    /** One-shot confirmation or failure text for the snackbar. */
    @NonNull
    public LiveData<FormError> toast() {
        return message;
    }

    // ------------------------------------------------------------------- paging

    /** First page; also used as the retry action. */
    public void loadFirstPage() {
        if (requestInFlight) {
            return;
        }
        accumulator = new PagedAccumulator<>(drugRepository.paging(filter.getValue(), errorMapper));
        requestInFlight = true;
        pagingState.setValue(PagingUiState.initial());
        run(accumulator.refresh(), true);
    }

    /** Called when the list nears its end. */
    public void loadNextPage() {
        PagedAccumulator<DrugResponse> current = accumulator;
        if (requestInFlight || current == null || !current.canLoadMore()) {
            return;
        }
        requestInFlight = true;
        pagingState.setValue(PagingUiState.appending());
        run(current.loadNext(), false);
    }

    /** Pull-to-refresh: same filters, fresh first page. */
    public void refresh() {
        refreshing.setValue(true);
        loadFirstPage();
    }

    private void run(@NonNull Single<PagedAccumulator.Outcome<DrugResponse>> call,
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

    // ------------------------------------------------------------- filter edits

    public void onSearchChanged(@Nullable String text) {
        applyFilter(filter.getValue().withSearch(normalize(text)));
    }

    public void setCategory(@Nullable Long categoryId) {
        applyFilter(filter.getValue().withCategory(categoryId));
    }

    public void setDosageForm(@Nullable String dosageForm) {
        applyFilter(filter.getValue().withDosageForm(
                dosageForm == null || dosageForm.isEmpty() ? null : dosageForm));
    }

    /** null = active and inactive, true = active only, false = inactive only. */
    public void setActive(@Nullable Boolean active) {
        applyFilter(filter.getValue().withActive(active));
    }

    public void setLowStock(@Nullable Boolean lowStock) {
        applyFilter(filter.getValue().withLowStock(lowStock));
    }

    /** Only the properties the backend documents; anything else is ignored there. */
    public void setSort(@Nullable String sort) {
        applyFilter(filter.getValue().withSort(sort));
    }

    public void clearFilters() {
        applyFilter(filter.getValue().cleared());
    }

    private void applyFilter(@NonNull DrugRepository.Filter next) {
        filter.setValue(next);
        loadFirstPage();
    }

    /** Called when the screen opens; primes the category cache once. */
    public void loadCategories() {
        if (!categories.getValue().isEmpty()) {
            return;
        }
        disposables.add(categoryRepository.listOrEmpty()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(categories::setValue,
                        throwable -> categories.setValue(Collections.emptyList())));
    }

    /** Only ADMIN/PHARMACIST may create, edit or deactivate drugs. */
    public boolean canManageCatalog() {
        return role().canManageCatalog();
    }

    @NonNull
    public Role role() {
        Session session = sessionManager.current();
        return session == null ? Role.EMPLOYEE : session.getRole();
    }

    /**
     * Flips {@code active} on a drug. Deactivation is a soft delete
     * ({@code DELETE /drugs/{id}}); reactivating is a normal update carrying the current
     * fields so nothing else is lost.
     */
    public void setDrugActive(@NonNull DrugResponse drug, boolean active) {
        if (!canManageCatalog()) {
            message.setValue(FormError.of(R.string.error_no_permission));
            return;
        }
        if (drug.getId() == null) {
            return;
        }
        long id = drug.getId();

        Single<DrugResponse> call = active
                ? drugRepository.update(id, UpdateDrugRequest.withActive(drug, true))
                : drugRepository.deactivate(id).andThen(drugRepository.get(id));

        disposables.add(call
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        updated -> {
                            message.setValue(FormError.of(active
                                    ? R.string.drug_reactivated : R.string.drug_deactivated));
                            loadFirstPage();
                        },
                        throwable -> message.setValue(
                                FormError.of(NetworkCall.asApiError(throwable).getMessage()))));
    }

    @Nullable
    private static String normalize(@Nullable String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    protected void onCleared() {
        disposables.clear();
    }
}