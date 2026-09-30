package com.larv.pharmacy.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** ISO week (Monday..Sunday) profit summary. */
public record WeeklyProfitResponse(
        LocalDate from,
        LocalDate to,
        long salesCount,
        BigDecimal revenue,
        BigDecimal cost,
        BigDecimal profit) {
}
