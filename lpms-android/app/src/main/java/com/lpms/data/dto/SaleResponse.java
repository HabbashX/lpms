package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code SaleResponse} — {@code GET /sales}, {@code GET /sales/{id}},
 * {@code POST /sales}.
 *
 * <p><b>Cost/profit disclosure:</b> the server returns {@code cost} and
 * {@code profit} to every authenticated role. The UI must render those two fields
 * only for ADMIN/PHARMACIST; EMPLOYEE screens show subtotal / total / amountPaid /
 * amountDue only.</p>
 */
public final class SaleResponse {

    @SerializedName("id")
    private final Long id;

    @SerializedName("createdAt")
    private final Instant createdAt;

    @SerializedName("customerId")
    private final Long customerId;

    @SerializedName("customerName")
    private final String customerName;

    @SerializedName("createdBy")
    private final String createdBy;

    @SerializedName("paymentMethod")
    private final String paymentMethod;

    @SerializedName("status")
    private final String status;

    @SerializedName("subtotal")
    private final BigDecimal subtotal;

    @SerializedName("discount")
    private final BigDecimal discount;

    @SerializedName("total")
    private final BigDecimal total;

    @SerializedName("amountPaid")
    private final BigDecimal amountPaid;

    @SerializedName("amountDue")
    private final BigDecimal amountDue;

    @SerializedName("cost")
    private final BigDecimal cost;

    @SerializedName("profit")
    private final BigDecimal profit;

    @SerializedName("refundedTotal")
    private final BigDecimal refundedTotal;

    @SerializedName("items")
    private final List<SaleItemResponse> items;

    public SaleResponse(@Nullable Long id,
                        @Nullable Instant createdAt,
                        @Nullable Long customerId,
                        @Nullable String customerName,
                        @Nullable String createdBy,
                        @Nullable String paymentMethod,
                        @Nullable String status,
                        @Nullable BigDecimal subtotal,
                        @Nullable BigDecimal discount,
                        @Nullable BigDecimal total,
                        @Nullable BigDecimal amountPaid,
                        @Nullable BigDecimal amountDue,
                        @Nullable BigDecimal cost,
                        @Nullable BigDecimal profit,
                        @Nullable BigDecimal refundedTotal,
                        @Nullable List<SaleItemResponse> items) {
        this.id = id;
        this.createdAt = createdAt;
        this.customerId = customerId;
        this.customerName = customerName;
        this.createdBy = createdBy;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.subtotal = subtotal;
        this.discount = discount;
        this.total = total;
        this.amountPaid = amountPaid;
        this.amountDue = amountDue;
        this.cost = cost;
        this.profit = profit;
        this.refundedTotal = refundedTotal;
        this.items = items == null ? new ArrayList<>() : items;
    }

    @Nullable
    public Long getId() {
        return id;
    }

    @Nullable
    public Instant getCreatedAt() {
        return createdAt;
    }

    @Nullable
    public Long getCustomerId() {
        return customerId;
    }

    @Nullable
    public String getCustomerName() {
        return customerName;
    }

    /** Username of the cashier who recorded the sale. */
    @Nullable
    public String getCreatedBy() {
        return createdBy;
    }

    @NonNull
    public PaymentMethod paymentMethod() {
        return PaymentMethod.fromNullable(paymentMethod);
    }

    @Nullable
    public String getPaymentMethod() {
        return paymentMethod;
    }

    @NonNull
    public SaleStatus saleStatus() {
        return SaleStatus.fromNullable(status);
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    @Nullable
    public BigDecimal getSubtotal() {
        return subtotal;
    }

    @Nullable
    public BigDecimal getDiscount() {
        return discount;
    }

    @Nullable
    public BigDecimal getTotal() {
        return total;
    }

    @Nullable
    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    @Nullable
    public BigDecimal getAmountDue() {
        return amountDue;
    }

    /** ADMIN/PHARMACIST only in the UI. */
    @Nullable
    public BigDecimal getCost() {
        return cost;
    }

    /** ADMIN/PHARMACIST only in the UI. */
    @Nullable
    public BigDecimal getProfit() {
        return profit;
    }

    @Nullable
    public BigDecimal getRefundedTotal() {
        return refundedTotal;
    }

    @NonNull
    public List<SaleItemResponse> getItems() {
        return items;
    }

    public boolean hasRefunds() {
        BigDecimal refunded = getRefundedTotal();
        return refunded != null && refunded.signum() > 0;
    }
}