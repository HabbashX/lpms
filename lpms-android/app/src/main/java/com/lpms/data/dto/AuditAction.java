package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * {@code AuditAction} enum, exactly as the backend declares it. Rendered as filter
 * chips on the audit screen.
 *
 * <p>{@link #UNKNOWN} is a client-only fallback so an action added by a newer server
 * renders with a generic label instead of crashing the list.</p>
 */
public enum AuditAction {

    @SerializedName("LOGIN")
    LOGIN,

    @SerializedName("LOGOUT")
    LOGOUT,

    @SerializedName("CHANGE_PASSWORD")
    CHANGE_PASSWORD,

    @SerializedName("CREATE_USER")
    CREATE_USER,

    @SerializedName("UPDATE_USER")
    UPDATE_USER,

    @SerializedName("CREATE_DRUG")
    CREATE_DRUG,

    @SerializedName("UPDATE_DRUG")
    UPDATE_DRUG,

    @SerializedName("DELETE_DRUG")
    DELETE_DRUG,

    @SerializedName("CREATE_CATEGORY")
    CREATE_CATEGORY,

    @SerializedName("UPDATE_CATEGORY")
    UPDATE_CATEGORY,

    @SerializedName("DELETE_CATEGORY")
    DELETE_CATEGORY,

    @SerializedName("PURCHASE_STOCK")
    PURCHASE_STOCK,

    @SerializedName("CREATE_CUSTOMER")
    CREATE_CUSTOMER,

    @SerializedName("UPDATE_CUSTOMER")
    UPDATE_CUSTOMER,

    @SerializedName("DELETE_CUSTOMER")
    DELETE_CUSTOMER,

    @SerializedName("CREATE_SALE")
    CREATE_SALE,

    @SerializedName("REFUND")
    REFUND,

    @SerializedName("CUSTOMER_PAYMENT")
    CUSTOMER_PAYMENT,

    @SerializedName("CUSTOMER_ADJUSTMENT")
    CUSTOMER_ADJUSTMENT,

    @SerializedName("UPDATE_SETTINGS")
    UPDATE_SETTINGS,

    /** Client-only: never sent, never used as a filter value. */
    UNKNOWN;

    /** Value sent as the {@code action} query parameter; UNKNOWN maps to null. */
    @Nullable
    public String wireValue() {
        return this == UNKNOWN ? null : name();
    }

    @NonNull
    public static AuditAction fromNullable(@Nullable String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        for (AuditAction action : values()) {
            if (action != UNKNOWN && action.name().equalsIgnoreCase(raw.trim())) {
                return action;
            }
        }
        return UNKNOWN;
    }

    /** Groups the audit screen's filter chips. */
    public enum Group {
        AUTH,
        USERS,
        DRUGS,
        CUSTOMERS,
        SALES,
        SYSTEM
    }

    @NonNull
    public Group group() {
        switch (this) {
            case LOGIN:
            case LOGOUT:
            case CHANGE_PASSWORD:
                return Group.AUTH;
            case CREATE_USER:
            case UPDATE_USER:
                return Group.USERS;
            case CREATE_DRUG:
            case UPDATE_DRUG:
            case DELETE_DRUG:
            case CREATE_CATEGORY:
            case UPDATE_CATEGORY:
            case DELETE_CATEGORY:
            case PURCHASE_STOCK:
                return Group.DRUGS;
            case CREATE_CUSTOMER:
            case UPDATE_CUSTOMER:
            case DELETE_CUSTOMER:
            case CUSTOMER_PAYMENT:
            case CUSTOMER_ADJUSTMENT:
                return Group.CUSTOMERS;
            case CREATE_SALE:
            case REFUND:
                return Group.SALES;
            case UPDATE_SETTINGS:
                return Group.SYSTEM;
            case UNKNOWN:
            default:
                return Group.SYSTEM;
        }
    }
}