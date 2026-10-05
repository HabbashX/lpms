package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;

/**
 * {@code GET /reports/profit?preset&from&to} and the shared shape of the daily,
 * weekly and monthly variants: {@code {from, to, salesCount, revenue, cost, profit}}.
 *
 * <p>{@code GET /reports/profit/daily} instead reports a single {@code date}.</p>
 *
 * <p>ADMIN/PHARMACIST only. Margin is computed client-side as {@code profit / revenue};
 * the backend does not send it.</p>
 */
public final class ProfitReportResponse {

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

    public ProfitReportResponse(@Nullable java.time.LocalDate from,
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

    /** {@code profit / revenue} as a fraction; null when revenue is zero/absent. */
    @Nullable
    public BigDecimal marginFraction() {
        BigDecimal rev = revenue;
        BigDecimal pro = profit;
        if (rev == null || pro == null || rev.signum() == 0) {
            return null;
        }
        return pro.divide(rev, 6, java.math.RoundingMode.HALF_UP);
    }

    /** {@code profit / revenue} as a percentage, e.g. {@code 12.34}. */
    @Nullable
    public BigDecimal marginPercent() {
        BigDecimal fraction = marginFraction();
        return fraction == null
                ? null
                : fraction.multiply(BigDecimal.valueOf(100))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    @NonNull
    public static BigDecimal zeroIfNull(@Nullable BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}