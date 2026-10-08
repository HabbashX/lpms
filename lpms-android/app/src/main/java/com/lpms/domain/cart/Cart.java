package com.lpms.domain.cart;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.data.dto.PaymentMethod;
import com.lpms.data.dto.DrugResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The POS cart: an ordered, duplicate-free set of lines plus the sale-level inputs.
 *
 * <p>Duplicate {@code drugId}s are merged on add — the server rejects them with 400
 * {@code INVALID_SALE}. Every mutation returns a new immutable {@link Cart} so the
 * ViewModel can publish snapshots without defensive copying.</p>
 */
public final class Cart {

    private final List<CartLine> lines;
    private final BigDecimal discount;
    private final PaymentMethod paymentMethod;
    private final Long customerId;
    private final String customerName;

    private Cart(@NonNull List<CartLine> lines,
                 @NonNull BigDecimal discount,
                 @NonNull PaymentMethod paymentMethod,
                 @Nullable Long customerId,
                 @Nullable String customerName) {
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
        this.discount = discount;
        this.paymentMethod = paymentMethod;
        this.customerId = customerId;
        this.customerName = customerName;
    }

    @NonNull
    public static Cart empty() {
        return new Cart(new ArrayList<>(), BigDecimal.ZERO, PaymentMethod.CASH, null, null);
    }

    @NonNull
    public List<CartLine> lines() {
        return lines;
    }

    @NonNull
    public BigDecimal getDiscount() {
        return discount;
    }

    @NonNull
    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    @Nullable
    public Long getCustomerId() {
        return customerId;
    }

    @Nullable
    public String getCustomerName() {
        return customerName;
    }

    public boolean hasCustomer() {
        return customerId != null;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public int lineCount() {
        return lines.size();
    }

    public int totalQuantity() {
        int total = 0;
        for (CartLine line : lines) {
            total += line.getQuantity();
        }
        return total;
    }

    @NonNull
    public CartTotals totals(@NonNull BigDecimal amountPaid) {
        List<BigDecimal> lineTotals = new ArrayList<>(lines.size());
        for (CartLine line : lines) {
            lineTotals.add(line.lineTotal());
        }
        return CartTotals.of(lineTotals, discount, amountPaid);
    }

    /** Adds a drug, or bumps the quantity when it is already in the cart. */
    @NonNull
    public Cart add(@NonNull DrugResponse drug) {
        long drugId = drug.getId() == null ? 0L : drug.getId();
        for (int i = 0; i < lines.size(); i++) {
            CartLine line = lines.get(i);
            if (line.getDrugId() == drugId) {
                List<CartLine> next = new ArrayList<>(lines);
                next.set(i, line.withQuantity(line.getQuantity() + 1));
                return new Cart(next, discount, paymentMethod, customerId, customerName);
            }
        }
        List<CartLine> next = new ArrayList<>(lines);
        next.add(new CartLine(drug, 1));
        return new Cart(next, discount, paymentMethod, customerId, customerName);
    }

    @NonNull
    public Cart setQuantity(long drugId, int quantity) {
        List<CartLine> next = new ArrayList<>(lines);
        for (int i = 0; i < next.size(); i++) {
            CartLine line = next.get(i);
            if (line.getDrugId() != drugId) {
                continue;
            }
            // Removing the line entirely is cheaper than keeping a zero-quantity row that
            // the server would reject with "quantity must be positive".
            if (quantity <= 0) {
                next.remove(i);
            } else {
                next.set(i, line.withQuantity(quantity));
            }
            return new Cart(next, discount, paymentMethod, customerId, customerName);
        }
        return this;
    }

    /** Per-line price override; null restores the drug default. */
    @NonNull
    public Cart setLinePrice(long drugId, @Nullable BigDecimal price) {
        List<CartLine> next = new ArrayList<>(lines);
        for (int i = 0; i < next.size(); i++) {
            CartLine line = next.get(i);
            if (line.getDrugId() == drugId) {
                next.set(i, line.withPriceOverride(price));
                return new Cart(next, discount, paymentMethod, customerId, customerName);
            }
        }
        return this;
    }

    @NonNull
    public Cart remove(long drugId) {
        List<CartLine> next = new ArrayList<>(lines);
        next.removeIf(line -> line.getDrugId() == drugId);
        return new Cart(next, discount, paymentMethod, customerId, customerName);
    }

    @NonNull
    public Cart withDiscount(@NonNull BigDecimal value) {
        return new Cart(lines, value.max(BigDecimal.ZERO), paymentMethod, customerId, customerName);
    }

    @NonNull
    public Cart withPaymentMethod(@NonNull PaymentMethod value) {
        return new Cart(lines, discount, value, customerId, customerName);
    }

    /**
     * Attaches (or clears) the customer for this sale. A credit sale must keep the
     * customer, so switching away from CREDIT drops the link rather than leaving stale
     * debt attribution on screen.
     */
    @NonNull
    public Cart withCustomer(@Nullable Long id, @Nullable String name) {
        if (paymentMethod != PaymentMethod.CREDIT && id == null) {
            return new Cart(lines, discount, paymentMethod, null, null);
        }
        return new Cart(lines, discount, paymentMethod, id, name);
    }

    /** Clears lines but keeps the payment method (common for the next customer). */
    @NonNull
    public Cart cleared() {
        return new Cart(new ArrayList<>(), discount, paymentMethod, customerId, customerName);
    }

    /**
     * Everything that must hold before checkout, mirroring the server rules.
     *
     * @return human-readable problems, empty when the sale is submittable
     */
    @NonNull
    public List<String> validate(@NonNull BigDecimal amountPaid) {
        List<String> problems = new ArrayList<>();
        if (lines.isEmpty()) {
            problems.add("cart is empty");
            return problems;
        }
        for (CartLine line : lines) {
            if (!line.isActive()) {
                problems.add(line.getDrugName() + " is inactive");
                continue;
            }
            if (line.getQuantity() <= 0) {
                problems.add(line.getDrugName() + " needs a quantity");
                continue;
            }
            if (line.isPriceMissing()) {
                problems.add(line.getDrugName() + " has no selling price — enter one");
                continue;
            }
            if (line.exceedsStock()) {
                problems.add(line.getDrugName() + " exceeds available stock");
            }
        }
        if (discount.signum() < 0) {
            problems.add("discount cannot be negative");
        }
        CartTotals totals = totals(amountPaid);
        if (discount.compareTo(totals.getSubtotal()) > 0) {
            problems.add("discount cannot exceed the subtotal");
        }
        if (paymentMethod == PaymentMethod.CREDIT && customerId == null) {
            problems.add("credit sales require a customer");
        }
        if (customerId == null && !totals.isPaidInFull()) {
            // No customer means no debt can be recorded: the server rejects this.
            problems.add("a sale without a customer must be paid in full");
        }
        if (amountPaid.signum() < 0) {
            problems.add("amount paid cannot be negative");
        }
        // The server rejects amountPaid above the sale total, so it is caught here rather
        // than as a failed confirmation. This is easy to hit: the paid field is prefilled
        // with the total, so lowering a line price after typing it leaves the old, larger
        // figure in place.
        if (amountPaid.compareTo(totals.getTotal()) > 0) {
            problems.add("amount paid is more than the sale total");
        }
        return problems;
    }

    public boolean canSubmit(@NonNull BigDecimal amountPaid) {
        return validate(amountPaid).isEmpty();
    }
}