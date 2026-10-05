package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * {@code GET /reports/profit/daily?date} → {@code {date, salesCount, revenue, cost, profit}}.
 *
 * <p>Used by the reports bar chart: the client calls it once per day, which is only
 * acceptable for ranges ≤ 31 days.</p>
 */
public final class DailyProfitResponse {

    @SerializedName("date")
    private final LocalDate date;

    @SerializedName("salesCount")
    private final Long salesCount;

    @SerializedName("revenue")
    private final BigDecimal revenue;

    @SerializedName("cost")
    private final BigDecimal cost;

    @SerializedName("profit")
    private final BigDecimal profit;

    public DailyProfitResponse(@Nullable LocalDate date,
                               @Nullable Long salesCount,
                               @Nullable BigDecimal revenue,
                               @Nullable BigDecimal cost,
                               @Nullable BigDecimal profit) {
        this.date = date;
        this.salesCount = salesCount;
        this.revenue = revenue;
        this.cost = cost;
        this.profit = profit;
    }

    @Nullable
    public LocalDate getDate() {
        return date;
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

    /** Converts to the generic range shape so the chart can hold one row type. */
    @NonNull
    public ProfitReportResponse asRange() {
        return new ProfitReportResponse(date, date, salesCount, revenue, cost, profit);
    }
}