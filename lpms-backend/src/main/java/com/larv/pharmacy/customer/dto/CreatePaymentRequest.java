package com.larv.pharmacy.customer.dto;

import com.larv.pharmacy.common.domain.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreatePaymentRequest(
        @NotNull(message = "amount is required")
        @Positive(message = "amount must be positive")
        BigDecimal amount,

        @NotNull(message = "paymentMethod is required")
        PaymentMethod paymentMethod,

        @Size(max = 255) String notes) {
}
