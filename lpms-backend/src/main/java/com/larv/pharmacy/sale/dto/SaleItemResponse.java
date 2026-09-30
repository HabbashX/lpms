package com.larv.pharmacy.sale.dto;

import java.math.BigDecimal;

public record SaleItemResponse(
        Long id,
        Long drugId,
        String drugName,
        int quantity,
        BigDecimal unitSellingPrice,
        BigDecimal unitCostPrice,
        BigDecimal discountAmount,
        BigDecimal revenue,
        BigDecimal cost,
        BigDecimal profit,
        int refundedQuantity) {
}
