package com.larv.pharmacy.inventory;

import com.larv.pharmacy.common.util.MoneyUtil;

import java.math.BigDecimal;

/**
 * Weighted-average (moving average) inventory costing.
 *
 * <p>The cost basis of remaining stock is
 * {@code sum(remainingQuantity * unitPurchasePrice) / sum(remainingQuantity)},
 * which — unlike a plain average of purchase prices — correctly accounts for
 * differing quantities per purchase.</p>
 */
public final class CostingService {

    private CostingService() {
    }

    public static CostBasis calculate(Iterable<StockBatch> batches) {
        int totalQuantity = 0;
        BigDecimal totalCost = BigDecimal.ZERO;
        for (StockBatch batch : batches) {
            totalQuantity += batch.getRemainingQuantity();
            totalCost = totalCost.add(batch.remainingValue());
        }
        BigDecimal weightedAverage = totalQuantity > 0
                ? totalCost.divide(BigDecimal.valueOf(totalQuantity),
                        MoneyUtil.UNIT_COST_SCALE, MoneyUtil.ROUNDING)
                : BigDecimal.ZERO.setScale(MoneyUtil.UNIT_COST_SCALE);
        return new CostBasis(totalQuantity, totalCost, weightedAverage);
    }

    public record CostBasis(int totalQuantity, BigDecimal totalInventoryCost, BigDecimal weightedAverageCost) {
    }
}
