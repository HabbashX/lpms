package com.larv.pharmacy.sale.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RefundResponse(
        Long id,
        Long saleId,
        Instant createdAt,
        String reason,
        BigDecimal totalAmount,
        BigDecimal totalCost,
        BigDecimal totalProfit,
        String createdBy,
        List<RefundItemResponse> items) {
}
