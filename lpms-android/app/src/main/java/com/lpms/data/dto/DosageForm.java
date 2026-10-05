package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * {@code DosageForm} enum, exactly as the backend declares it.
 *
 * <p>Serialised by name; unknown values coming back from a newer server are mapped
 * to {@link #OTHER} instead of throwing.</p>
 */
public enum DosageForm {

    @SerializedName("TABLET")
    TABLET,

    @SerializedName("CAPSULE")
    CAPSULE,

    @SerializedName("SYRUP")
    SYRUP,

    @SerializedName("SOLUTION")
    SOLUTION,

    @SerializedName("INJECTION")
    INJECTION,

    @SerializedName("CREAM")
    CREAM,

    @SerializedName("OINTMENT")
    OINTMENT,

    @SerializedName("GEL")
    GEL,

    @SerializedName("DROPS")
    DROPS,

    @SerializedName("INHALER")
    INHALER,

    @SerializedName("SPRAY")
    SPRAY,

    @SerializedName("POWDER")
    POWDER,

    @SerializedName("PATCH")
    PATCH,

    @SerializedName("SUPPOSITORY")
    SUPPOSITORY,

    @SerializedName("OTHER")
    OTHER;

    @NonNull
    public static DosageForm fromNullable(@Nullable String raw) {
        if (raw == null) {
            return OTHER;
        }
        for (DosageForm form : values()) {
            if (form.name().equalsIgnoreCase(raw.trim())) {
                return form;
            }
        }
        return OTHER;
    }

    @NonNull
    public String wireValue() {
        return name();
    }
}