package com.lpms.ui.categories;

import androidx.annotation.NonNull;

import com.lpms.R;
import com.lpms.core.error.ApiError;
import com.lpms.core.error.ApiErrorCodes;
import com.lpms.core.network.NetworkCall;
import com.lpms.core.ui.UiState;
import com.lpms.data.dto.CategoryResponse;
import com.lpms.data.repo.CategoryRepository;
import com.lpms.ui.common.FormError;

import javax.inject.Inject;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * Category management (ADMIN/PHARMACIST).
 *
 * <p>Handles the two documented conflicts: {@code CATEGORY_ALREADY_EXISTS} on create/rename
 * (case-insensitive) and {@code CATEGORY_IN_USE} on delete.</p>
 */
@HiltViewModel
public final class CategoriesViewModel extends ViewModel {

    private final CategoryRepository categoryRepository;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<UiState<java.util.List<CategoryResponse>>> state =
            new MutableLiveData<>();
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<FormError> message = new MutableLiveData<>();

    @Inject
    public CategoriesViewModel(@NonNull CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @NonNull
    public LiveData<UiState<java.util.List<CategoryResponse>>> state() {
        return state;
    }

    @NonNull
    public LiveData<Boolean> busy() {
        return busy;
    }

    @NonNull
    public LiveData<FormError> message() {
        return message;
    }

    public void load() {
        state.setValue(UiState.loading());
        fetch();
    }

    private void fetch() {
        disposables.add(categoryRepository.list()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        items -> state.setValue(items.isEmpty()
                                ? UiState.<java.util.List<CategoryResponse>>empty()
                                : UiState.content(items)),
                        throwable -> state.setValue(
                                UiState.error(NetworkCall.asApiError(throwable)))));
    }

    /** @return true when accepted (the list reloads on its own through the cache). */
    public void create(@NonNull String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 100) {
            message.setValue(FormError.of(trimmed.isEmpty()
                    ? R.string.category_error_name_required : R.string.category_error_name_too_long));
            return;
        }
        busy.setValue(true);
        disposables.add(categoryRepository.create(trimmed)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        created -> {
                            busy.setValue(false);
                            fetch();
                        },
                        throwable -> onWriteFailed(throwable)));
    }

    public void rename(@NonNull CategoryResponse category, @NonNull String name) {
        String trimmed = name.trim();
        if (category.getId() == null) {
            return;
        }
        if (trimmed.isEmpty() || trimmed.length() > 100) {
            message.setValue(FormError.of(trimmed.isEmpty()
                    ? R.string.category_error_name_required : R.string.category_error_name_too_long));
            return;
        }
        busy.setValue(true);
        disposables.add(categoryRepository.rename(category.getId(), trimmed)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        updated -> {
                            busy.setValue(false);
                            fetch();
                        },
                        throwable -> onWriteFailed(throwable)));
    }

    /** Deleting a category still used by a drug is 409 {@code CATEGORY_IN_USE}. */
    public void delete(@NonNull CategoryResponse category) {
        if (category.getId() == null) {
            return;
        }
        busy.setValue(true);
        disposables.add(categoryRepository.delete(category.getId())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> {
                            busy.setValue(false);
                            fetch();
                        },
                        throwable -> onWriteFailed(throwable)));
    }

    private void onWriteFailed(@NonNull Throwable throwable) {
        busy.setValue(false);
        ApiError error = NetworkCall.asApiError(throwable);
        String nameMessage = error.messageFor("name");
        if (nameMessage != null) {
            message.setValue(FormError.of(nameMessage));
            return;
        }
        message.setValue(FormError.of(error.getMessage()));
    }

    /** True for the conflict that means "still referenced by a drug". */
    public static boolean isInUse(@NonNull ApiError error) {
        return ApiErrorCodes.CATEGORY_IN_USE.equals(error.getCode());
    }

    @Override
    protected void onCleared() {
        disposables.clear();
    }
}