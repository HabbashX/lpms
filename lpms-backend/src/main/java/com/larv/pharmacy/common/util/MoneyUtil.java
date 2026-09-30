package com.larv.pharmacy.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Central place for every monetary calculation. Money is always {@link BigDecimal};
 * binary floating point types are never used.
 *
 * <p>Scales: aggregates (totals, reports) use 2 decimal places, unit costs keep
 * 4 decimal places so weighted averages do not lose precision.</p>
 */
public final class MoneyUtil {

    public static final int MONEY_SCALE = 2;
    public static final int UNIT_COST_SCALE = 4;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private MoneyUtil() {
    }

    public static BigDecimal round2(BigDecimal value) {
        return nz(value).setScale(MONEY_SCALE, ROUNDING);
    }

    public static BigDecimal round4(BigDecimal value) {
        return nz(value).setScale(UNIT_COST_SCALE, ROUNDING);
    }

    /** Null-safe: returns {@link BigDecimal#ZERO} for null. */
    public static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Null-safe multiplication of a unit price by a quantity. */
    public static BigDecimal multiply(BigDecimal unitPrice, long quantity) {
        return nz(unitPrice).multiply(BigDecimal.valueOf(quantity));
    }

    /** Division with the unit-cost scale; returns zero when the divisor is zero. */
    public static BigDecimal divide(BigDecimal numerator, BigDecimal denominator, int scale) {
        BigDecimal den = nz(denominator);
        if (den.signum() == 0) {
            return BigDecimal.ZERO.setScale(scale, ROUNDING);
        }
        return nz(numerator).divide(den, scale, ROUNDING);
    }

    public static boolean isNegative(BigDecimal value) {
        return nz(value).signum() < 0;
    }
}
