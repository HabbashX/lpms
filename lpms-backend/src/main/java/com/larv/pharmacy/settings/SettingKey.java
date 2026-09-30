package com.larv.pharmacy.settings;

/**
 * Pharmacy configuration keys managed by administrators through
 * {@code GET/PUT /api/v1/settings}.
 */
public enum SettingKey {

    INVENTORY_ALLOW_EXPIRED_SALES("inventory.allow_expired_sales", "false", Type.BOOLEAN,
            "Allow selling stock from expired batches"),
    CUSTOMER_ALLOW_NEGATIVE_BALANCE("customer.allow_negative_balance", "false", Type.BOOLEAN,
            "Allow customer account balances to go below zero (customer credit)"),
    INVENTORY_EXPIRING_SOON_DAYS("inventory.expiring_soon_days", "30", Type.INTEGER,
            "Default window (days) used by the expiring stock report");

    public enum Type {
        BOOLEAN, INTEGER
    }

    private final String key;
    private final String defaultValue;
    private final Type type;
    private final String description;

    SettingKey(String key, String defaultValue, Type type, String description) {
        this.key = key;
        this.defaultValue = defaultValue;
        this.type = type;
        this.description = description;
    }

    public String getKey() {
        return key;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public Type getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public static SettingKey fromKey(String key) {
        for (SettingKey value : values()) {
            if (value.key.equals(key)) {
                return value;
            }
        }
        throw new com.larv.pharmacy.common.exception.InvalidRequestException(
                "UNKNOWN_SETTING", "Unknown setting: " + key);
    }
}
