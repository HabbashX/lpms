package com.larv.pharmacy.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MonthlyProfitResponse(
        int year,
        int month,
        LocalDate from,
        LocalDate to,
        long salesCount,
        BigDecimal revenue,
        BigDecimal cost,
        BigDecimal profit,
        BigDecimal averageSaleValue) {
}
