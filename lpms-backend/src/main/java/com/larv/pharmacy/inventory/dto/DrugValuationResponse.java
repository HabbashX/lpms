package com.larv.pharmacy.inventory.dto;

import com.larv.pharmacy.inventory.ValuationRow;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Weighted-average valuation of one drug's remaining stock.
 * Example: 100 units at 10 + 100 units at 15 = 200 units / 2500.00 = 12.50.
 */
public record DrugValuationResponse(
        Long drugId,
        String drugName,
        long totalQuantity,
        BigDecimal totalInventoryCost,
        BigDecimal weightedAverageCost) {

    public static DrugValuationResponse from(ValuationRow row) {
        BigDecimal cost = row.totalInventoryCost() == null ? BigDecimal.ZERO : row.totalInventoryCost();
        long quantity = row.totalQuantity() == null ? 0L : row.totalQuantity();
        BigDecimal average = quantity > 0
                ? cost.divide(BigDecimal.valueOf(quantity), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return new DrugValuationResponse(
                row.drugId(),
                row.drugName(),
                quantity,
                cost.setScale(2, RoundingMode.HALF_UP),
                average.setScale(2, RoundingMode.HALF_UP));
    }

    public static DrugValuationResponse empty(Long drugId, String drugName) {
        return new DrugValuationResponse(drugId, drugName, 0L, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
