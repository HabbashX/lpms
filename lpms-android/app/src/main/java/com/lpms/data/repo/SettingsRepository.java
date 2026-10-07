package com.lpms.data.repo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.core.network.NetworkCall;
import com.lpms.data.api.SettingsApi;
import com.lpms.data.dto.SettingResponse;
import com.lpms.data.dto.UpdateSettingsRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Pharmacy settings. ADMIN only on the server.
 *
 * <p>{@code PUT /settings} takes a map and replaces the values it contains, leaving the
 * rest alone. {@link #update} sends only the keys that actually changed, so saving the
 * screen cannot silently reset a setting the form did not show.</p>
 *
 * <p>The backend returns the full, updated set after a write, so the caller re-renders from
 * the response rather than assuming its local state stuck.</p>
 */
@Singleton
public final class SettingsRepository {

    private final SettingsApi settingsApi;

    @Inject
    public SettingsRepository(@NonNull SettingsApi settingsApi) {
        this.settingsApi = settingsApi;
    }

    @NonNull
    public Single<List<SettingResponse>> getAll() {
        return settingsApi.getAll()
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    /** Sends only the changed keys; an empty map is refused rather than sent. */
    @NonNull
    public Single<List<SettingResponse>> update(@NonNull Map<String, String> changes) {
        if (changes.isEmpty()) {
            return Single.error(new IllegalArgumentException("no settings changed"));
        }
        return settingsApi.update(new UpdateSettingsRequest(new LinkedHashMap<>(changes)))
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    /** Convenience for a single setting. */
    @NonNull
    public Single<List<SettingResponse>> updateOne(@NonNull String key, @NonNull String value) {
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put(key, value);
        return update(changes);
    }

    /**
     * Values that differ between what the server holds and what the form now shows.
     *
     * <p>Only keys actually present in {@code edited} are considered, so a field the screen
     * does not render is never sent.</p>
     */
    @NonNull
    public static Map<String, String> changesFor(@NonNull List<SettingResponse> loaded,
                                                 @NonNull Map<String, String> edited) {
        Map<String, String> changes = new LinkedHashMap<>();
        for (SettingResponse setting : loaded) {
            String key = setting.getKey();
            if (key == null || !edited.containsKey(key)) {
                continue;
            }
            String newValue = edited.get(key);
            String currentValue = setting.getValue();
            String normalisedCurrent = currentValue == null ? "" : currentValue.trim();
            String normalisedNew = newValue == null ? "" : newValue.trim();
            if (!normalisedNew.equals(normalisedCurrent)) {
                changes.put(key, normalisedNew);
            }
        }
        return changes;
    }
}