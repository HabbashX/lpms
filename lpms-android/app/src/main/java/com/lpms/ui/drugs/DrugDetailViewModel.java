package com.lpms.ui.drugs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.R;
import com.lpms.core.auth.Role;
import com.lpms.core.auth.Session;
import com.lpms.core.network.NetworkCall;
import com.lpms.core.ui.UiState;
import com.lpms.data.dto.DrugResponse;
import com.lpms.data.dto.UpdateDrugRequest;
import com.lpms.data.repo.AuthRepository;
import com.lpms.data.repo.DrugRepository;
import com.lpms.ui.common.FormError;

import javax.inject.Inject;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/** Drug detail: one GET, plus the role-gated edit / deactivate actions. */
@HiltViewModel
public final class DrugDetailViewModel extends ViewModel {

    private final DrugRepository drugRepository;
    private final AuthRepository authRepository;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final long drugId;
    private final MutableLiveData<UiState<DrugResponse>> state = new MutableLiveData<>();
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<FormError> message = new MutableLiveData<>();
    private final MutableLiveData<Boolean> closed = new MutableLiveData<>(false);

    @Inject
    public DrugDetailViewModel(@NonNull DrugRepository drugRepository,
                               @NonNull AuthRepository authRepository,
                               @NonNull SavedStateHandle handle) {
        this.drugRepository = drugRepository;
        this.authRepository = authRepository;
        Long id = handle.get(DrugListFragment.ARG_DRUG_ID);
        this.drugId = id == null ? 0L : id;
    }

    @NonNull
    public LiveData<UiState<DrugResponse>> state() {
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

    /** Emits true once the drug is deactivated, so the screen can pop itself. */
    @NonNull
    public LiveData<Boolean> closed() {
        return closed;
    }

    public boolean canManageCatalog() {
        Session session = authRepository.sessions().current();
        Role role = session == null ? Role.EMPLOYEE : session.getRole();
        return role.canManageCatalog();
    }

    public void load() {
        state.setValue(UiState.loading());
        disposables.add(drugRepository.get(drugId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        drug -> state.setValue(UiState.content(drug)),
                        throwable -> state.setValue(
                                UiState.error(NetworkCall.asApiError(throwable)))));
    }

    public void setActive(@NonNull DrugResponse drug, boolean active) {
        if (!canManageCatalog()) {
            message.setValue(FormError.of(R.string.error_no_permission));
            return;
        }
        busy.setValue(true);
        disposables.add(drugRepository.update(drug.getId(), UpdateDrugRequest.withActive(drug, active))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        updated -> {
                            busy.setValue(false);
                            message.setValue(FormError.of(active
                                    ? R.string.drug_reactivated : R.string.drug_deactivated));
                            if (active) {
                                state.setValue(UiState.content(updated));
                            } else {
                                // Deactivated drugs stay visible in the list as inactive,
                                // so the detail screen just closes.
                                closed.setValue(true);
                            }
                        },
                        throwable -> {
                            busy.setValue(false);
                            message.setValue(FormError.of(
                                    NetworkCall.asApiError(throwable).getMessage()));
                        }));
    }

    @Override
    protected void onCleared() {
        disposables.clear();
    }
}