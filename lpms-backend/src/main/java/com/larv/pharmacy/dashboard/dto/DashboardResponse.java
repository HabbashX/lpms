package com.larv.pharmacy.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardResponse(
        Today today,
        Month month,
        Inventory inventory,
        Customers customers,
        List<TopSellingDrug> topSellingDrugs) {

    public record Today(BigDecimal revenue, BigDecimal profit, long sales) {
    }

    public record Month(int year, int month, BigDecimal revenue, BigDecimal profit, long sales) {
    }

    public record Inventory(BigDecimal value, long lowStock, long expiringSoon) {
    }

    public record Customers(long count, BigDecimal totalDebt) {
    }

    public record TopSellingDrug(Long drugId, String drugName, long quantity, BigDecimal revenue) {
    }
}
