package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;

/**
 * {@code GET /reports/profit/monthly?year&month} — adds {@code year}, {@code month}
 * and {@code averageSaleValue} to the range shape:
 * {@code {year, month, from, to, salesCount, revenue, cost, profit, averageSaleValue}}.
 */
public final class MonthlyProfitResponse {

    @SerializedName("year")
    private final Integer year;

    @SerializedName("month")
    private final Integer month;

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

    @SerializedName("averageSaleValue")
    private final BigDecimal averageSaleValue;

    public MonthlyProfitResponse(@Nullable Integer year,
                                 @Nullable Integer month,
                                 @Nullable java.time.LocalDate from,
                                 @Nullable java.time.LocalDate to,
                                 @Nullable Long salesCount,
                                 @Nullable BigDecimal revenue,
                                 @Nullable BigDecimal cost,
                                 @Nullable BigDecimal profit,
                                 @Nullable BigDecimal averageSaleValue) {
        this.year = year;
        this.month = month;
        this.from = from;
        this.to = to;
        this.salesCount = salesCount;
        this.revenue = revenue;
        this.cost = cost;
        this.profit = profit;
        this.averageSaleValue = averageSaleValue;
    }

    @Nullable
    public Integer getYear() {
        return year;
    }

    @Nullable
    public Integer getMonth() {
        return month;
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

    @Nullable
    public BigDecimal getAverageSaleValue() {
        return averageSaleValue;
    }

    @NonNull
    public ProfitReportResponse asRange() {
        return new ProfitReportResponse(from, to, salesCount, revenue, cost, profit);
    }
}