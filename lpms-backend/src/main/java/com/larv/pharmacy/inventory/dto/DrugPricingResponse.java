package com.larv.pharmacy.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Cost and pricing view of one drug. {@code weightedAverageCost} is computed over all remaining
 * batches (quantity-weighted). {@code suggestedSellingPrice} is only present when
 * {@code profitPerUnit} or {@code marginPercent} was requested.
 */
public record DrugPricingResponse(
        Long drugId,
        String drugName,
        long totalQuantity,
        BigDecimal totalInventoryCost,
        BigDecimal weightedAverageCost,
        BigDecimal sellingPrice,
        BigDecimal profitPerUnit,
        BigDecimal marginPercent,
        BigDecimal markupPercent,
        BigDecimal expectedTotalProfit,
        BigDecimal suggestedSellingPrice,
        List<BatchCost> batches) {

    public record BatchCost(
            Long batchId,
            String batchNumber,
            int remainingQuantity,
            BigDecimal unitPurchasePrice,
            LocalDate expirationDate) {
    }
}
