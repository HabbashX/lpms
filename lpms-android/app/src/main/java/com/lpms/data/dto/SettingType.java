package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/** Setting value type: drives which editor the settings screen renders. */
public enum SettingType {

    @SerializedName("BOOLEAN")
    BOOLEAN,

    @SerializedName("INTEGER")
    INTEGER,

    /** Client-only fallback so an unknown type renders read-only instead of crashing. */
    UNKNOWN;

    @NonNull
    public static SettingType fromNullable(@Nullable String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        for (SettingType type : values()) {
            if (type != UNKNOWN && type.name().equalsIgnoreCase(raw.trim())) {
                return type;
            }
        }
        return UNKNOWN;
    }
}