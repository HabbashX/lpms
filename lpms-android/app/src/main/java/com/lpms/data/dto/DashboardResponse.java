package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code GET /dashboard} — one call that powers the whole home screen.
 *
 * <p>The backend returns profit figures to <b>every</b> authenticated role, so
 * {@link #getToday()} and {@link #getMonth()} expose profit unconditionally; the UI
 * is responsible for hiding those tiles for EMPLOYEE.</p>
 */
public final class DashboardResponse {

    @SerializedName("today")
    private final PeriodSummary today;

    @SerializedName("month")
    private final PeriodSummary month;

    @SerializedName("inventory")
    private final InventorySummary inventory;

    @SerializedName("customers")
    private final CustomerSummary customers;

    @SerializedName("topSellingDrugs")
    private final List<TopSellingDrug> topSellingDrugs;

    public DashboardResponse(@Nullable PeriodSummary today,
                             @Nullable PeriodSummary month,
                             @Nullable InventorySummary inventory,
                             @Nullable CustomerSummary customers,
                             @Nullable List<TopSellingDrug> topSellingDrugs) {
        this.today = today;
        this.month = month;
        this.inventory = inventory;
        this.customers = customers;
        this.topSellingDrugs = topSellingDrugs == null
                ? new ArrayList<>() : topSellingDrugs;
    }

    @NonNull
    public PeriodSummary today() {
        return today == null ? PeriodSummary.empty() : today;
    }

    /** Carries {@code year} and {@code month} as well as the totals. */
    @NonNull
    public PeriodSummary month() {
        return month == null ? PeriodSummary.empty() : month;
    }

    @NonNull
    public InventorySummary inventory() {
        return inventory == null ? InventorySummary.empty() : inventory;
    }

    @NonNull
    public CustomerSummary customers() {
        return customers == null ? CustomerSummary.empty() : customers;
    }

    @NonNull
    public List<TopSellingDrug> getTopSellingDrugs() {
        return topSellingDrugs;
    }

    /** Shared shape of {@code today} and {@code month}. */
    public static final class PeriodSummary {

        @SerializedName("year")
        private final Integer year;

        @SerializedName("month")
        private final Integer month;

        @SerializedName("revenue")
        private final BigDecimal revenue;

        /** Hide from EMPLOYEE. */
        @SerializedName("profit")
        private final BigDecimal profit;

        @SerializedName("sales")
        private final Long sales;

        public PeriodSummary(@Nullable Integer year,
                             @Nullable Integer month,
                             @Nullable BigDecimal revenue,
                             @Nullable BigDecimal profit,
                             @Nullable Long sales) {
            this.year = year;
            this.month = month;
            this.revenue = revenue;
            this.profit = profit;
            this.sales = sales;
        }

        static PeriodSummary empty() {
            return new PeriodSummary(null, null, BigDecimal.ZERO, BigDecimal.ZERO, 0L);
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
        public BigDecimal getRevenue() {
            return revenue;
        }

        /** ADMIN/PHARMACIST only in the UI. */
        @Nullable
        public BigDecimal getProfit() {
            return profit;
        }

        public long getSales() {
            return sales == null ? 0L : sales;
        }
    }

    public static final class InventorySummary {

        @SerializedName("value")
        private final BigDecimal value;

        @SerializedName("lowStock")
        private final Long lowStock;

        @SerializedName("expiringSoon")
        private final Long expiringSoon;

        public InventorySummary(@Nullable BigDecimal value,
                                @Nullable Long lowStock,
                                @Nullable Long expiringSoon) {
            this.value = value;
            this.lowStock = lowStock;
            this.expiringSoon = expiringSoon;
        }

        static InventorySummary empty() {
            return new InventorySummary(BigDecimal.ZERO, 0L, 0L);
        }

        @Nullable
        public BigDecimal getValue() {
            return value;
        }

        public long getLowStock() {
            return lowStock == null ? 0L : lowStock;
        }

        public long getExpiringSoon() {
            return expiringSoon == null ? 0L : expiringSoon;
        }
    }

    public static final class CustomerSummary {

        @SerializedName("count")
        private final Long count;

        @SerializedName("totalDebt")
        private final BigDecimal totalDebt;

        public CustomerSummary(@Nullable Long count, @Nullable BigDecimal totalDebt) {
            this.count = count;
            this.totalDebt = totalDebt;
        }

        static CustomerSummary empty() {
            return new CustomerSummary(0L, BigDecimal.ZERO);
        }

        public long getCount() {
            return count == null ? 0L : count;
        }

        @Nullable
        public BigDecimal getTotalDebt() {
            return totalDebt;
        }
    }

    /** One row of {@code topSellingDrugs}. */
    public static final class TopSellingDrug {

        @SerializedName("drugId")
        private final Long drugId;

        @SerializedName("drugName")
        private final String drugName;

        @SerializedName("quantity")
        private final Long quantity;

        @SerializedName("revenue")
        private final BigDecimal revenue;

        public TopSellingDrug(@Nullable Long drugId,
                              @Nullable String drugName,
                              @Nullable Long quantity,
                              @Nullable BigDecimal revenue) {
            this.drugId = drugId;
            this.drugName = drugName;
            this.quantity = quantity;
            this.revenue = revenue;
        }

        @Nullable
        public Long getDrugId() {
            return drugId;
        }

        @Nullable
        public String getDrugName() {
            return drugName;
        }

        public long getQuantity() {
            return quantity == null ? 0L : quantity;
        }

        @Nullable
        public BigDecimal getRevenue() {
            return revenue;
        }
    }

    /** Never-null list accessor for adapters. */
    @NonNull
    public List<TopSellingDrug> topSellingDrugs() {
        return Collections.unmodifiableList(topSellingDrugs);
    }
}