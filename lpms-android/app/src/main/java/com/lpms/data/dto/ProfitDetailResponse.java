package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of {@code GET /reports/profit/details} (paginated
 * {@code PageResponse<ProfitDetailResponse>}).
 *
 * <p>Filter parameters on that endpoint are named {@code drug}, {@code category},
 * {@code employee} and {@code customer} — <b>not</b> {@code drugId} etc. — and carry
 * ids.</p>
 */
public final class ProfitDetailResponse {

    @SerializedName("saleId")
    private final Long saleId;

    @SerializedName("date")
    private final LocalDate date;

    @SerializedName("drugId")
    private final Long drugId;

    @SerializedName("drug")
    private final String drug;

    @SerializedName("quantity")
    private final Long quantity;

    @SerializedName("revenue")
    private final BigDecimal revenue;

    @SerializedName("cost")
    private final BigDecimal cost;

    @SerializedName("profit")
    private final BigDecimal profit;

    @SerializedName("paymentMethod")
    private final String paymentMethod;

    @SerializedName("employee")
    private final String employee;

    @SerializedName("customerId")
    private final Long customerId;

    @SerializedName("customer")
    private final String customer;

    public ProfitDetailResponse(@Nullable Long saleId,
                                @Nullable LocalDate date,
                                @Nullable Long drugId,
                                @Nullable String drug,
                                @Nullable Long quantity,
                                @Nullable BigDecimal revenue,
                                @Nullable BigDecimal cost,
                                @Nullable BigDecimal profit,
                                @Nullable String paymentMethod,
                                @Nullable String employee,
                                @Nullable Long customerId,
                                @Nullable String customer) {
        this.saleId = saleId;
        this.date = date;
        this.drugId = drugId;
        this.drug = drug;
        this.quantity = quantity;
        this.revenue = revenue;
        this.cost = cost;
        this.profit = profit;
        this.paymentMethod = paymentMethod;
        this.employee = employee;
        this.customerId = customerId;
        this.customer = customer;
    }

    @Nullable
    public Long getSaleId() {
        return saleId;
    }

    @Nullable
    public LocalDate getDate() {
        return date;
    }

    @Nullable
    public Long getDrugId() {
        return drugId;
    }

    @Nullable
    public String getDrug() {
        return drug;
    }

    public long getQuantity() {
        return quantity == null ? 0L : quantity;
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
    public PaymentMethod paymentMethod() {
        return PaymentMethod.fromNullable(paymentMethod);
    }

    @Nullable
    public String getPaymentMethod() {
        return paymentMethod;
    }

    @Nullable
    public String getEmployee() {
        return employee;
    }

    @Nullable
    public Long getCustomerId() {
        return customerId;
    }

    @Nullable
    public String getCustomer() {
        return customer;
    }
}