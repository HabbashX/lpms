package com.larv.pharmacy.report;

import com.larv.pharmacy.common.domain.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One sale line in the detailed profit report, net of its refunds.
 * {@code createdBy} is resolved to a username by the service.
 */
public record ProfitDetailRow(
        Long saleId,
        Instant createdAt,
        Long drugId,
        String drugName,
        PaymentMethod paymentMethod,
        Long createdBy,
        Long customerId,
        String customerName,
        int quantity,
        BigDecimal revenue,
        BigDecimal cost) {
}
