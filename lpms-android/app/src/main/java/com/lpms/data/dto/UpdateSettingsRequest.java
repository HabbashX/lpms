package com.lpms.data.dto;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code PUT /settings} body (ADMIN only) — a map of key → <b>string</b> value:
 *
 * <pre>{@code {"settings":{"inventory.allow_expired_sales":"true"}}}</pre>
 *
 * <p>Only the keys present in the map are updated. Values are always strings, even for
 * integer settings. An empty map is 400 {@code EMPTY_SETTINGS}; an unknown key is
 * {@code UNKNOWN_SETTING}; a bad value for a known key is {@code INVALID_SETTING}.</p>
 */
public final class UpdateSettingsRequest {

    @SerializedName("settings")
    private final Map<String, String> settings;

    public UpdateSettingsRequest(@NonNull Map<String, String> settings) {
        this.settings = new LinkedHashMap<>(settings);
    }

    @NonNull
    public Map<String, String> getSettings() {
        return Collections.unmodifiableMap(settings);
    }

    public boolean isEmpty() {
        return settings.isEmpty();
    }

    /** Convenience for the single-key "toggle and save" flow. */
    @NonNull
    public static UpdateSettingsRequest single(@NonNull String key, @NonNull String value) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(key, value);
        return new UpdateSettingsRequest(map);
    }
}