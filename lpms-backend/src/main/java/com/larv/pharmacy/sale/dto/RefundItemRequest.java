package com.larv.pharmacy.sale.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RefundItemRequest(
        @NotNull(message = "saleItemId is required")
        Long saleItemId,

        @NotNull(message = "quantity is required")
        @Positive(message = "quantity must be positive")
        Integer quantity) {
}
