package com.larv.pharmacy.sale.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Refund request. When {@code items} is omitted the whole remaining quantity
 * of every sale line is refunded.
 */
public record CreateRefundRequest(
        List<RefundItemRequest> items,
        @Size(max = 255) String reason) {
}
