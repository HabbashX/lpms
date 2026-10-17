package com.lpms.local.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.math.BigDecimal;

/**
 * A recorded sale; mirrors the fields of {@link com.lpms.data.dto.SaleResponse} that the
 * app reads.
 *
 * <p>{@code profitTotal} is kept for admin/pharmacist views and is recomputed on write,
 * exactly as the backend does it: revenue minus cost of the units that left.</p>
 */
@Entity(tableName = "sales")
public final class SaleEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    @NonNull
    @ColumnInfo(name = "created_at")
    private final String createdAt;

    @Nullable
    @ColumnInfo(name = "customer_id")
    private final Long customerId;

    @Nullable
    @ColumnInfo(name = "customer_name")
    private final String customerName;

    /** {@link com.lpms.data.dto.PaymentMethod#wireValue()}. */
    @NonNull
    @ColumnInfo(name = "payment_method")
    private final String paymentMethod;

    @NonNull
    @ColumnInfo(name = "subtotal")
    private final BigDecimal subtotal;

    @NonNull
    @ColumnInfo(name = "discount")
    private final BigDecimal discount;

    @NonNull
    @ColumnInfo(name = "total")
    private final BigDecimal total;

    @NonNull
    @ColumnInfo(name = "amount_paid")
    private final BigDecimal amountPaid;

    @NonNull
    @ColumnInfo(name = "amount_due")
    private final BigDecimal amountDue;

    @NonNull
    @ColumnInfo(name = "cost_total")
    private final BigDecimal costTotal;

    @NonNull
    @ColumnInfo(name = "profit_total")
    private final BigDecimal profitTotal;

    @NonNull
    @ColumnInfo(name = "status")
    private final String status;

    public SaleEntity(long id,
                      @NonNull String createdAt,
                      @Nullable Long customerId,
                      @Nullable String customerName,
                      @NonNull String paymentMethod,
                      @NonNull BigDecimal subtotal,
                      @NonNull BigDecimal discount,
                      @NonNull BigDecimal total,
                      @NonNull BigDecimal amountPaid,
                      @NonNull BigDecimal amountDue,
                      @NonNull BigDecimal costTotal,
                      @NonNull BigDecimal profitTotal,
                      @NonNull String status) {
        this.id = id;
        this.createdAt = createdAt;
        this.customerId = customerId;
        this.customerName = customerName;
        this.paymentMethod = paymentMethod;
        this.subtotal = subtotal;
        this.discount = discount;
        this.total = total;
        this.amountPaid = amountPaid;
        this.amountDue = amountDue;
        this.costTotal = costTotal;
        this.profitTotal = profitTotal;
        this.status = status;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getCreatedAt() {
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

    @NonNull
    public String getPaymentMethod() {
        return paymentMethod;
    }

    @NonNull
    public BigDecimal getSubtotal() {
        return subtotal;
    }

    @NonNull
    public BigDecimal getDiscount() {
        return discount;
    }

    @NonNull
    public BigDecimal getTotal() {
        return total;
    }

    @NonNull
    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    @NonNull
    public BigDecimal getAmountDue() {
        return amountDue;
    }

    @NonNull
    public BigDecimal getCostTotal() {
        return costTotal;
    }

    @NonNull
    public BigDecimal getProfitTotal() {
        return profitTotal;
    }

    @NonNull
    public String getStatus() {
        return status;
    }
}
