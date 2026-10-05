package com.lpms.ui.home;

import androidx.annotation.NonNull;

import com.lpms.core.auth.Role;
import com.lpms.core.auth.SessionManager;
import com.lpms.core.error.ApiError;
import com.lpms.core.network.NetworkCall;
import com.lpms.core.ui.UiState;
import com.lpms.data.dto.DashboardResponse;
import com.lpms.data.repo.AuthRepository;
import com.lpms.data.repo.DashboardRepository;

import javax.inject.Inject;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * Dashboard tiles. One request feeds the whole screen; pull-to-refresh re-runs it.
 *
 * <p>{@link #canSeeProfit()} mirrors the rule that the backend sends profit to every
 * role while the UI must hide it from EMPLOYEE.</p>
 */
@HiltViewModel
public final class DashboardViewModel extends ViewModel {

    private final DashboardRepository dashboardRepository;
    private final SessionManager sessionManager;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<UiState<DashboardResponse>> state =
            new MutableLiveData<>(UiState.loading());
    private final MutableLiveData<Boolean> refreshing = new MutableLiveData<>(false);

    @Inject
    public DashboardViewModel(@NonNull DashboardRepository dashboardRepository,
                              @NonNull AuthRepository authRepository) {
        this.dashboardRepository = dashboardRepository;
        this.sessionManager = authRepository.sessions();
    }

    @NonNull
    public LiveData<UiState<DashboardResponse>> state() {
        return state;
    }

    /** True while a pull-to-refresh is in flight (content stays visible). */
    @NonNull
    public LiveData<Boolean> refreshing() {
        return refreshing;
    }

    /** Profit tiles are rendered only for ADMIN/PHARMACIST. */
    public boolean canSeeProfit() {
        return currentRole().canSeeProfit();
    }

    public Role currentRole() {
        return sessionManager.current() == null
                ? Role.EMPLOYEE
                : sessionManager.current().getRole();
    }

    public void load() {
        if (Boolean.TRUE.equals(refreshing.getValue())) {
            return;
        }
        state.setValue(UiState.loading());
        disposables.add(dashboardRepository.load()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        response -> state.setValue(UiState.content(response)),
                        throwable -> state.setValue(UiState.error(
                                NetworkCall.asApiError(throwable)))));
    }

    /** Pull-to-refresh: keeps the current content on screen while reloading. */
    public void refresh() {
        if (Boolean.TRUE.equals(refreshing.getValue())) {
            return;
        }
        refreshing.setValue(true);
        disposables.add(dashboardRepository.load()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        response -> {
                            refreshing.setValue(false);
                            state.setValue(UiState.content(response));
                        },
                        throwable -> {
                            refreshing.setValue(false);
                            // Show the error only if there is nothing to keep on screen;
                            // otherwise a snackbar-style message is enough.
                            if (!state.getValue().isContent()) {
                                state.setValue(UiState.error(NetworkCall.asApiError(throwable)));
                            }
                        }));
    }

    /** Full error for a snackbar when a refresh failed over existing content. */
    @NonNull
    public static ApiError errorOf(@NonNull Throwable throwable) {
        return NetworkCall.asApiError(throwable);
    }

    @Override
    protected void onCleared() {
        disposables.clear();
    }
}