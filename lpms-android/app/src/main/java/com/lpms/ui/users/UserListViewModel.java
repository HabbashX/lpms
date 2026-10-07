package com.lpms.ui.users;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.lpms.R;
import com.lpms.core.error.ApiErrorMapper;
import com.lpms.core.network.NetworkCall;
import com.lpms.core.network.PagedAccumulator;
import com.lpms.data.dto.UserResponse;
import com.lpms.data.repo.UserRepository;
import com.lpms.ui.common.FormError;
import com.lpms.ui.common.PagingUiState;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/** Staff list: role and status filters, paging, plus the enable/disable action. */
@HiltViewModel
public final class UserListViewModel extends ViewModel {

    private final UserRepository userRepository;
    private final ApiErrorMapper errorMapper;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<UserRepository.Filter> filter =
            new MutableLiveData<>(UserRepository.Filter.none());
    private final MutableLiveData<List<UserResponse>> items =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<PagingUiState> pagingState =
            new MutableLiveData<>(PagingUiState.initial());
    private final MutableLiveData<Boolean> refreshing = new MutableLiveData<>(false);
    private final MutableLiveData<FormError> message = new MutableLiveData<>();

    @Nullable
    private PagedAccumulator<UserResponse> accumulator;
    private boolean requestInFlight;

    @Inject
    public UserListViewModel(@NonNull UserRepository userRepository,
                             @NonNull ApiErrorMapper errorMapper) {
        this.userRepository = userRepository;
        this.errorMapper = errorMapper;
    }

    @NonNull
    public LiveData<List<UserResponse>> items() {
        return items;
    }

    @NonNull
    public LiveData<PagingUiState> pagingState() {
        return pagingState;
    }

    @NonNull
    public LiveData<UserRepository.Filter> filter() {
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
        accumulator = new PagedAccumulator<>(userRepository.paging(filter.getValue(), errorMapper));
        requestInFlight = true;
        pagingState.setValue(PagingUiState.initial());
        run(accumulator.refresh(), true);
    }

    public void loadNextPage() {
        PagedAccumulator<UserResponse> current = accumulator;
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

    private void run(@NonNull Single<PagedAccumulator.Outcome<UserResponse>> call,
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

    public void setRole(@Nullable String wireValue) {
        applyFilter(filter.getValue().withRole(
                wireValue == null || wireValue.isEmpty() ? null : wireValue));
    }

    /** null = any status, true = enabled only. */
    public void setEnabled(@Nullable Boolean enabled) {
        applyFilter(filter.getValue().withEnabled(enabled));
    }

    /** Enabling or disabling an account; the backend invalidates the user's live tokens. */
    public void setEnabled(@NonNull UserResponse user, boolean enabled) {
        if (user.getId() == null) {
            return;
        }
        disposables.add(userRepository.setEnabled(user.getId(), enabled)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> {
                            message.setValue(FormError.of(enabled
                                    ? R.string.user_enabled : R.string.user_disabled_message));
                            loadFirstPage();
                        },
                        throwable -> message.setValue(FormError.of(
                                NetworkCall.asApiError(throwable).getMessage()))));
    }

    public void resetPassword(@NonNull UserResponse user, @Nullable String password) {
        if (user.getId() == null) {
            return;
        }
        String trimmed = password == null ? "" : password.trim();
        if (trimmed.length() < 8) {
            message.setValue(FormError.of(R.string.user_error_password_short));
            return;
        }
        disposables.add(userRepository.resetPassword(user.getId(), trimmed)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> message.setValue(FormError.of(R.string.user_password_reset)),
                        throwable -> message.setValue(FormError.of(
                                NetworkCall.asApiError(throwable).getMessage()))));
    }

    public void clearFilters() {
        applyFilter(UserRepository.Filter.none());
    }

    private void applyFilter(@NonNull UserRepository.Filter next) {
        filter.setValue(next);
        loadFirstPage();
    }

    @Override
    protected void onCleared() {
        disposables.clear();
    }
}