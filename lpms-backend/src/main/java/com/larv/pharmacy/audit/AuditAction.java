package com.larv.pharmacy.audit;

/**
 * Actions recorded in the audit trail. The list is a superset of the actions
 * required by the specification; audit rows are append-only and can only be
 * read (never modified) through the API.
 */
public enum AuditAction {
    LOGIN,
    LOGOUT,
    CHANGE_PASSWORD,
    CREATE_USER,
    UPDATE_USER,
    CREATE_DRUG,
    UPDATE_DRUG,
    DELETE_DRUG,
    CREATE_CATEGORY,
    UPDATE_CATEGORY,
    DELETE_CATEGORY,
    PURCHASE_STOCK,
    CREATE_CUSTOMER,
    UPDATE_CUSTOMER,
    DELETE_CUSTOMER,
    CREATE_SALE,
    REFUND,
    CUSTOMER_PAYMENT,
    CUSTOMER_ADJUSTMENT,
    UPDATE_SETTINGS
}
