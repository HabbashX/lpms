package com.lpms.core.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * Server-side role. Read fresh from {@code GET /auth/me} on every app resume,
 * so a role change takes effect without a reinstall.
 */
public enum Role {

    @SerializedName("ADMIN")
    ADMIN,

    @SerializedName("PHARMACIST")
    PHARMACIST,

    @SerializedName("EMPLOYEE")
    EMPLOYEE;

    /** Unknown/absent roles must never grant privileges. */
    @NonNull
    public static Role fromNullable(@Nullable String raw) {
        if (raw == null) {
            return EMPLOYEE;
        }
        for (Role role : values()) {
            if (role.name().equalsIgnoreCase(raw.trim())) {
                return role;
            }
        }
        return EMPLOYEE;
    }

    public boolean isAdmin() {
        return this == ADMIN;
    }

    public boolean isPharmacist() {
        return this == PHARMACIST;
    }

    public boolean isEmployee() {
        return this == EMPLOYEE;
    }

    /** Cost/profit data is exposed by the backend to every role; the UI must hide it. */
    public boolean canSeeProfit() {
        return this == ADMIN || this == PHARMACIST;
    }

    /** Drugs, categories, stock purchase, drug pricing/cost analysis. */
    public boolean canManageCatalog() {
        return this == ADMIN || this == PHARMACIST;
    }

    /** Sale refunds and customer ledger adjustments. */
    public boolean canRefund() {
        return this == ADMIN || this == PHARMACIST;
    }

    /** {@code /reports/**}. */
    public boolean canViewReports() {
        return this == ADMIN || this == PHARMACIST;
    }

    /** Users, audit log, settings. */
    public boolean canAdminister() {
        return this == ADMIN;
    }
}