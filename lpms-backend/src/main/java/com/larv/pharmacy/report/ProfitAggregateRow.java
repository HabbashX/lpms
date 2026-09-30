package com.larv.pharmacy.report;

import java.math.BigDecimal;

/** Raw aggregate of sales in a period, net of the refunds attached to them. */
public record ProfitAggregateRow(Long salesCount, BigDecimal revenue, BigDecimal cost) {
}
