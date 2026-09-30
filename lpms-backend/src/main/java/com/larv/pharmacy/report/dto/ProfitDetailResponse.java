package com.larv.pharmacy.report.dto;

import com.larv.pharmacy.common.domain.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

/** One line of the detailed profit report (sale date basis, net of refunds). */
public record ProfitDetailResponse(
        Long saleId,
        Instant date,
        Long drugId,
        String drug,
        long quantity,
        BigDecimal revenue,
        BigDecimal cost,
        BigDecimal profit,
        PaymentMethod paymentMethod,
        String employee,
        Long customerId,
        String customer) {
}
