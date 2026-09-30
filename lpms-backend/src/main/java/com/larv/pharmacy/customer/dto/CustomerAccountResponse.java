package com.larv.pharmacy.customer.dto;

import com.larv.pharmacy.common.dto.PageResponse;

import java.math.BigDecimal;

/**
 * Everything needed for the customer account page: identity, totals and the
 * chronologically ordered ledger with pagination.
 */
public record CustomerAccountResponse(
        CustomerResponse customer,
        BigDecimal totalPurchases,
        BigDecimal totalPaid,
        BigDecimal totalRefunds,
        BigDecimal currentDebt,
        PageResponse<TransactionResponse> transactions) {
}
