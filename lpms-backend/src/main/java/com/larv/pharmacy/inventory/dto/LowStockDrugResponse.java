package com.larv.pharmacy.inventory.dto;

public record LowStockDrugResponse(
        Long drugId,
        String name,
        int currentQuantity,
        int minimumStockLevel,
        String unit) {
}
