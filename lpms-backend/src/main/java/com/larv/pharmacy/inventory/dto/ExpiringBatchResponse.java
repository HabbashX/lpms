package com.larv.pharmacy.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpiringBatchResponse(
        Long batchId,
        Long drugId,
        String drugName,
        String batchNumber,
        LocalDate expirationDate,
        long daysUntilExpiration,
        boolean expired,
        int remainingQuantity,
        BigDecimal unitPurchasePrice,
        String supplier) {
}
