package com.larv.pharmacy.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Custom date-range profit report. {@code from} and {@code to} are inclusive
 * dates; internally the range is evaluated as {@code [from 00:00, to+1 00:00)}.
 */
public record ProfitReportResponse(
        LocalDate from,
        LocalDate to,
        long salesCount,
        BigDecimal revenue,
        BigDecimal cost,
        BigDecimal profit) {
}
