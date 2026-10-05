package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.time.Instant;

/**
 * One row of {@code GET /settings} (ADMIN only) — a plain, non-paginated list.
 *
 * <p>The screen is rendered <b>dynamically</b> from {@link #getType()}: a switch for
 * {@code BOOLEAN}, a numeric field for {@code INTEGER} (≥ 0). No key is hard-coded in
 * the UI.</p>
 *
 * <p>Known keys, for reference only:
 * {@code inventory.allow_expired_sales} (bool), {@code customer.allow_negative_balance}
 * (bool), {@code inventory.expiring_soon_days} (int).</p>
 */
public final class SettingResponse {

    @SerializedName("key")
    private final String key;

    /** Always a string, even for INTEGER and BOOLEAN settings. */
    @SerializedName("value")
    private final String value;

    @SerializedName("description")
    private final String description;

    @SerializedName("type")
    private final String type;

    @SerializedName("updatedBy")
    private final String updatedBy;

    @SerializedName("updatedAt")
    private final Instant updatedAt;

    public SettingResponse(@Nullable String key,
                           @Nullable String value,
                           @Nullable String description,
                           @Nullable String type,
                           @Nullable String updatedBy,
                           @Nullable Instant updatedAt) {
        this.key = key;
        this.value = value;
        this.description = description;
        this.type = type;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    @Nullable
    public String getKey() {
        return key;
    }

    @Nullable
    public String getValue() {
        return value;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    @Nullable
    public String getType() {
        return type;
    }

    @Nullable
    public String getUpdatedBy() {
        return updatedBy;
    }

    @Nullable
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @NonNull
    public SettingType settingType() {
        return SettingType.fromNullable(type);
    }

    public boolean isBoolean() {
        return settingType() == SettingType.BOOLEAN;
    }

    /** Never throws on malformed values; a non-boolean string reads as false. */
    public boolean booleanValue() {
        return Boolean.parseBoolean(value);
    }

    /** @return the integer value, or {@code fallback} when absent/malformed. */
    public int intValue(int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** The value to send back in {@code PUT /settings}; values are always strings. */
    @NonNull
    public String valueForTransport(boolean boolValue, int intValue) {
        return isBoolean() ? String.valueOf(boolValue) : String.valueOf(Math.max(0, intValue));
    }
}