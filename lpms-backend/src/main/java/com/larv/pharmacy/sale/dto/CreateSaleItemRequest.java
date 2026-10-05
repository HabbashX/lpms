package com.larv.pharmacy.sale.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateSaleItemRequest(
        @NotNull(message = "drugId is required")
        Long drugId,

        @NotNull(message = "quantity is required")
        @Positive(message = "quantity must be positive")
        Integer quantity,

        /** Optional: when omitted the drug's default selling price is used. */
        @Positive(message = "unitSellingPrice must be positive")
        BigDecimal unitSellingPrice) {
}
