package com.larv.pharmacy.sale.dto;

import java.math.BigDecimal;

public record RefundItemResponse(
        Long saleItemId,
        String drugName,
        int quantity,
        BigDecimal unitSellingPrice,
        BigDecimal unitCostPrice,
        BigDecimal amount,
        BigDecimal cost,
        BigDecimal profit) {
}
