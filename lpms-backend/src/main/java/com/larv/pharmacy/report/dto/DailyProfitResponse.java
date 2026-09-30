package com.larv.pharmacy.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyProfitResponse(
        LocalDate date,
        long salesCount,
        BigDecimal revenue,
        BigDecimal cost,
        BigDecimal profit) {
}
