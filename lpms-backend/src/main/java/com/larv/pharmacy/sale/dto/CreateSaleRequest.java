package com.larv.pharmacy.sale.dto;

import com.larv.pharmacy.common.domain.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public record CreateSaleRequest(
        Long customerId,

        @NotNull(message = "paymentMethod is required")
        PaymentMethod paymentMethod,

        @NotEmpty(message = "A sale must contain at least one item")
        List<@Valid CreateSaleItemRequest> items,

        @PositiveOrZero(message = "discount must not be negative")
        BigDecimal discount,

        @PositiveOrZero(message = "amountPaid must not be negative")
        BigDecimal amountPaid) {
}
