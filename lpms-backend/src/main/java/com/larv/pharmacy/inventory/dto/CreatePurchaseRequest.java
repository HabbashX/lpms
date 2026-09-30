package com.larv.pharmacy.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreatePurchaseRequest(
        @NotNull(message = "drugId is required")
        Long drugId,

        @NotNull(message = "quantity is required")
        @Positive(message = "quantity must be positive")
        Integer quantity,

        @NotNull(message = "unitPurchasePrice is required")
        @Positive(message = "unitPurchasePrice must be positive")
        java.math.BigDecimal unitPurchasePrice,

        @Size(max = 150) String supplier,
        @Size(max = 100) String batchNumber,
        LocalDate expirationDate) {
}
