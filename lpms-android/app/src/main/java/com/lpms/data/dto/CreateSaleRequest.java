package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code POST /sales} body → 201.
 *
 * <p>Client-side rules mirrored here (the server re-validates every one):</p>
 * <ul>
 *   <li>{@code items} must be non-empty and contain <b>no duplicate {@code drugId}</b>
 *       — duplicate lines are 400 {@code INVALID_SALE}, so the cart merges them.</li>
 *   <li>{@code quantity} &gt; 0; {@code unitSellingPrice} &gt; 0 when sent.</li>
 *   <li>{@code discount} is a <b>sale-level amount</b> (not a percentage) with
 *       0 ≤ discount ≤ subtotal.</li>
 *   <li>{@link PaymentMethod#CREDIT} requires {@code customerId}.</li>
 *   <li>{@code amountPaid} must be 0…total, and must be the full total when there is
 *       no customer. {@code amountDue = total − amountPaid} becomes customer debt.</li>
 * </ul>
 *
 * <p>Other server errors: 409 {@code INSUFFICIENT_STOCK},
 * {@code EXPIRED_STOCK} (unless {@code inventory.allow_expired_sales} is enabled),
 * {@code CUSTOMER_INACTIVE}; 400 {@code INVALID_SALE} for an inactive drug.</p>
 */
public final class CreateSaleRequest {

    @SerializedName("customerId")
    private final Long customerId;

    @SerializedName("paymentMethod")
    private final String paymentMethod;

    @SerializedName("items")
    private final List<CreateSaleItemRequest> items;

    @SerializedName("discount")
    private final BigDecimal discount;

    @SerializedName("amountPaid")
    private final BigDecimal amountPaid;

    @SerializedName("transfer")
    private final TransferDetails transfer;

    public CreateSaleRequest(@Nullable Long customerId,
                             @NonNull String paymentMethod,
                             @NonNull List<CreateSaleItemRequest> items,
                             @Nullable BigDecimal discount,
                             @Nullable BigDecimal amountPaid,
                             @Nullable TransferDetails transfer) {
        this.customerId = customerId;
        this.paymentMethod = paymentMethod;
        this.items = items == null ? new ArrayList<>() : items;
        this.discount = discount;
        this.amountPaid = amountPaid;
        this.transfer = transfer;
    }

    @Nullable
    public Long getCustomerId() {
        return customerId;
    }

    @NonNull
    public String getPaymentMethod() {
        return paymentMethod;
    }

    @NonNull
    public List<CreateSaleItemRequest> getItems() {
        return items;
    }

    /** Sale-level amount, never a percentage. Null is treated as zero server-side. */
    @Nullable
    public BigDecimal getDiscount() {
        return discount;
    }

    /** Null means "paid in full" (or 0 for a credit sale). */
    @Nullable
    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    /** Bank transfer destination; non-null only for {@link PaymentMethod#BANK_TRANSFER}. */
    @Nullable
    public TransferDetails getTransfer() {
        return transfer;
    }

    /** Local pre-flight checks; a violation is a cart bug the server would reject. */
    @NonNull
    public List<String> validateLocally() {
        List<String> problems = new ArrayList<>();
        if (items.isEmpty()) {
            problems.add("items must not be empty");
        }
        Set<Long> seen = new HashSet<>();
        for (CreateSaleItemRequest item : items) {
            if (!seen.add(item.getDrugId())) {
                problems.add("duplicate drugId " + item.getDrugId());
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                problems.add("quantity must be positive for drug " + item.getDrugId());
            }
            BigDecimal price = item.getUnitSellingPrice();
            if (price != null && price.signum() <= 0) {
                problems.add("unitSellingPrice must be positive for drug " + item.getDrugId());
            }
        }
        if (PaymentMethod.fromNullable(paymentMethod).requiresCustomer() && customerId == null) {
            problems.add("credit sale requires a customer");
        }
        if (discount != null && discount.signum() < 0) {
            problems.add("discount must not be negative");
        }
        if (amountPaid != null && amountPaid.signum() < 0) {
            problems.add("amountPaid must not be negative");
        }
        // The server pairs transfer details with BANK_TRANSFER in both directions:
        // required for a transfer, rejected on anything else.
        boolean isTransfer = PaymentMethod.fromNullable(paymentMethod) == PaymentMethod.BANK_TRANSFER;
        if (isTransfer && (transfer == null || !transfer.isComplete())) {
            problems.add("a bank transfer sale needs the provider, account name and account");
        }
        if (!isTransfer && transfer != null) {
            problems.add("transfer details only apply to a bank transfer sale");
        }
        return Collections.unmodifiableList(problems);
    }

    public boolean isLocallyValid() {
        return validateLocally().isEmpty();
    }
}