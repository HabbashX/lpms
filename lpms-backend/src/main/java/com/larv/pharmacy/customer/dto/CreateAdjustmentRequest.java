package com.larv.pharmacy.customer.dto;

import com.larv.pharmacy.customer.TransactionDirection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Manual ledger correction written by an administrator (e.g. written-off debt). */
public record CreateAdjustmentRequest(
        @NotNull(message = "amount is required")
        @Positive(message = "amount must be positive")
        BigDecimal amount,

        @NotNull(message = "direction is required")
        TransactionDirection direction,

        @NotBlank(message = "description is required")
        @Size(max = 255)
        String description) {
}
