package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;

/**
 * {@code GET /reports/profit/weekly?date} — the ISO week (Monday–Sunday) that
 * contains {@code date}: {@code {from, to, salesCount, revenue, cost, profit}}.
 */
public final class WeeklyProfitResponse {

    @SerializedName("from")
    private final java.time.LocalDate from;

    @SerializedName("to")
    private final java.time.LocalDate to;

    @SerializedName("salesCount")
    private final Long salesCount;

    @SerializedName("revenue")
    private final BigDecimal revenue;

    @SerializedName("cost")
    private final BigDecimal cost;

    @SerializedName("profit")
    private final BigDecimal profit;

    public WeeklyProfitResponse(@Nullable java.time.LocalDate from,
                                @Nullable java.time.LocalDate to,
                                @Nullable Long salesCount,
                                @Nullable BigDecimal revenue,
                                @Nullable BigDecimal cost,
                                @Nullable BigDecimal profit) {
        this.from = from;
        this.to = to;
        this.salesCount = salesCount;
        this.revenue = revenue;
        this.cost = cost;
        this.profit = profit;
    }

    @Nullable
    public java.time.LocalDate getFrom() {
        return from;
    }

    @Nullable
    public java.time.LocalDate getTo() {
        return to;
    }

    public long getSalesCount() {
        return salesCount == null ? 0L : salesCount;
    }

    @Nullable
    public BigDecimal getRevenue() {
        return revenue;
    }

    @Nullable
    public BigDecimal getCost() {
        return cost;
    }

    @Nullable
    public BigDecimal getProfit() {
        return profit;
    }

    @NonNull
    public ProfitReportResponse asRange() {
        return new ProfitReportResponse(from, to, salesCount, revenue, cost, profit);
    }
}