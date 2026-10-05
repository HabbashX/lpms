package com.larv.pharmacy.inventory;

import com.larv.pharmacy.common.exception.InvalidRequestException;
import com.larv.pharmacy.common.util.MoneyUtil;

import java.math.BigDecimal;

/**
 * Selling-price maths on top of the weighted-average cost.
 * Example: 300 units @ 300 plus 300 units @ 230 gives an average cost of 265;
 * a wanted profit of 50 per unit gives a selling price of 315.
 */
public final class PricingCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private PricingCalculator() {
    }

    /** cost + profit per unit. */
    public static BigDecimal priceForProfit(BigDecimal unitCost, BigDecimal profitPerUnit) {
        return MoneyUtil.round2(MoneyUtil.nz(unitCost).add(MoneyUtil.nz(profitPerUnit)));
    }

    /** Price so that profit is {@code marginPercent} of the selling price: cost / (1 - m/100). */
    public static BigDecimal priceForMargin(BigDecimal unitCost, BigDecimal marginPercent) {
        BigDecimal margin = MoneyUtil.nz(marginPercent);
        if (margin.signum() < 0 || margin.compareTo(HUNDRED) >= 0) {
            throw new InvalidRequestException("INVALID_MARGIN", "marginPercent must be at least 0 and below 100");
        }
        BigDecimal divisor = BigDecimal.ONE.subtract(margin.divide(HUNDRED, 6, MoneyUtil.ROUNDING));
        return MoneyUtil.round2(MoneyUtil.divide(unitCost, divisor, MoneyUtil.UNIT_COST_SCALE));
    }

    /** profit as a percentage of the selling price; zero when the price is zero. */
    public static BigDecimal marginPercent(BigDecimal unitCost, BigDecimal sellingPrice) {
        BigDecimal profit = MoneyUtil.nz(sellingPrice).subtract(MoneyUtil.nz(unitCost));
        return MoneyUtil.round2(MoneyUtil.divide(profit, sellingPrice, 6).multiply(HUNDRED));
    }

    /** profit as a percentage of the cost; zero when the cost is zero. */
    public static BigDecimal markupPercent(BigDecimal unitCost, BigDecimal sellingPrice) {
        BigDecimal profit = MoneyUtil.nz(sellingPrice).subtract(MoneyUtil.nz(unitCost));
        return MoneyUtil.round2(MoneyUtil.divide(profit, unitCost, 6).multiply(HUNDRED));
    }
}
