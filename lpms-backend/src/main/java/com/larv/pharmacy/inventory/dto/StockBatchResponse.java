package com.larv.pharmacy.inventory.dto;

import com.larv.pharmacy.inventory.StockBatch;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record StockBatchResponse(
        Long id,
        Long drugId,
        String drugName,
        int quantityReceived,
        int remainingQuantity,
        BigDecimal unitPurchasePrice,
        String supplier,
        String batchNumber,
        LocalDate expirationDate,
        Instant receivedAt,
        Instant createdAt) {

    public static StockBatchResponse from(StockBatch batch, String drugName) {
        return new StockBatchResponse(
                batch.getId(),
                batch.getDrug().getId(),
                drugName,
                batch.getQuantityReceived(),
                batch.getRemainingQuantity(),
                batch.getUnitPurchasePrice().setScale(2, java.math.RoundingMode.HALF_UP),
                batch.getSupplier(),
                batch.getBatchNumber(),
                batch.getExpirationDate(),
                batch.getReceivedAt(),
                batch.getCreatedAt());
    }
}
