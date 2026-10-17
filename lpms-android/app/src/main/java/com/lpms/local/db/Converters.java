package com.lpms.local.db;

import androidx.room.TypeConverter;

import java.math.BigDecimal;

/**
 * Stores money as its {@code toPlainString} form.
 *
 * <p>Text rather than REAL, on purpose: a floating-point column would make
 * {@code 0.10} and {@code 0.10} indistinguishable after rounding, and every figure in
 * this app is money that has to survive a sum without drifting. {@code plainString} keeps
 * the scale that was written, so a genuinely absent price stays distinguishable from
 * {@code 0}.</p>
 */
public final class Converters {

    private Converters() {
    }

    @TypeConverter
    public static String fromBigDecimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    @TypeConverter
    public static BigDecimal toBigDecimal(String value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException fallback) {
            // A corrupt or foreign-written cell must not crash the whole app. A zero
            // price is visible and correctable; an uninstall on every launch is not.
            return BigDecimal.ZERO;
        }
    }
}
