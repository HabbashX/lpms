package com.larv.pharmacy.inventory;

import java.math.BigDecimal;

/** Aggregated valuation of a set of inventory batches for one drug. */
public record ValuationRow(Long drugId, String drugName, Long totalQuantity, BigDecimal totalInventoryCost) {
}
