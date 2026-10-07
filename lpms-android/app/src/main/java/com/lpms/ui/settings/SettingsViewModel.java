package com.lpms.ui.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.R;
import com.lpms.core.network.NetworkCall;
import com.lpms.core.ui.UiState;
import com.lpms.data.dto.SettingResponse;
import com.lpms.data.repo.SettingsRepository;
import com.lpms.ui.common.FormError;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * Pharmacy settings.
 *
 * <p>The screen is built from whatever the server returns: each setting declares its own
 * type, so a boolean renders as a switch, an integer as a number field, and anything the
 * build does not recognise is shown read-only rather than being sent back unchanged.</p>
 *
 * <p>{@code PUT /settings} is a partial update over a map. Only keys the user actually
 * changed are sent, so saving cannot reset a setting this form does not show.</p>
 */
@HiltViewModel
public final class SettingsViewModel extends ViewModel {

    private final SettingsRepository settingsRepository;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<UiState<List<SettingResponse>>> state = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saving = new MutableLiveData<>(false);
    private final MutableLiveData<FormError> message = new MutableLiveData<>();

    /** The values currently in the form, keyed by setting key. */
    private final Map<String, String> edited = new LinkedHashMap<>();

    @Inject
    public SettingsViewModel(@NonNull SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    @NonNull
    public LiveData<UiState<List<SettingResponse>>> state() {
        return state;
    }

    @NonNull
    public LiveData<Boolean> saving() {
        return saving;
    }

    @NonNull
    public LiveData<FormError> message() {
        return message;
    }

    /** True when the user has unsaved edits, so the screen can guard the back button. */
    public boolean hasUnsavedChanges() {
        UiState<List<SettingResponse>> current = state.getValue();
        if (current == null || !current.isContent()) {
            return false;
        }
        List<SettingResponse> loaded = current.valueOrNull();
        return loaded != null && !SettingsRepository.changesFor(loaded, edited).isEmpty();
    }

    public void load() {
        state.setValue(UiState.loading());
        disposables.add(settingsRepository.getAll()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        settings -> {
                            // A reload starts from the server's values again, so a discarded
                            // edit does not linger in the diff.
                            edited.clear();
                            state.setValue(UiState.content(settings));
                        },
                        throwable -> state.setValue(
                                UiState.error(NetworkCall.asApiError(throwable)))));
    }

    public void refresh() {
        load();
    }

    public void onValueChanged(@NonNull String key, @Nullable String value) {
        edited.put(key, value == null ? "" : value.trim());
    }

    /**
     * Writes only what changed. A no-op when nothing did, so tapping Save twice cannot
     * send an empty update.
     */
    public void save() {
        if (Boolean.TRUE.equals(saving.getValue())) {
            return;
        }
        UiState<List<SettingResponse>> current = state.getValue();
        if (current == null || !current.isContent() || current.valueOrNull() == null) {
            return;
        }
        Map<String, String> changes = SettingsRepository.changesFor(current.valueOrNull(), edited);
        if (changes.isEmpty()) {
            message.setValue(FormError.of(R.string.settings_nothing_to_save));
            return;
        }

        saving.setValue(true);
        disposables.add(settingsRepository.update(changes)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        updated -> {
                            saving.setValue(false);
                            edited.clear();
                            message.setValue(FormError.of(R.string.settings_saved));
                            state.setValue(UiState.content(updated));
                        },
                        throwable -> {
                            saving.setValue(false);
                            message.setValue(FormError.of(
                                    NetworkCall.asApiError(throwable).getMessage()));
                        }));
    }

    /** The current form value for a key, falling back to what the server holds. */
    @NonNull
    public String valueFor(@NonNull SettingResponse setting) {
        String editedValue = edited.get(setting.getKey());
        return editedValue == null ? nullToEmpty(setting.getValue()) : editedValue;
    }

    @NonNull
    public List<SettingResponse> settingsOrEmpty() {
        UiState<List<SettingResponse>> current = state.getValue();
        if (current != null && current.isContent() && current.valueOrNull() != null) {
            return current.valueOrNull();
        }
        return Collections.emptyList();
    }

    @NonNull
    private static String nullToEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    @Override
    protected void onCleared() {
        disposables.clear();
    }
}