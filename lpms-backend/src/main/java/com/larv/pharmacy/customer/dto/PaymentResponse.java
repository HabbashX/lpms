package com.larv.pharmacy.customer.dto;

import com.larv.pharmacy.common.domain.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        Long customerId,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        String notes,
        BigDecimal balanceAfter,
        Instant createdAt) {
}
