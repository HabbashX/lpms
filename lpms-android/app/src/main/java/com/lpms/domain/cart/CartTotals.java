package com.lpms.domain.cart;

import androidx.annotation.NonNull;

import java.math.BigDecimal;

/**
 * Immutable snapshot of the cart money, recomputed on every cart change.
 *
 * <p>Rules, mirroring section 4.5 of the contract:</p>
 * <ul>
 *   <li>{@code subtotal} = Σ line totals;</li>
 *   <li>{@code discount} is a sale-level <b>amount</b> (never a percentage), and must
 *       satisfy 0 ≤ discount ≤ subtotal;</li>
 *   <li>{@code total} = subtotal − discount;</li>
 *   <li>{@code amountDue} = total − amountPaid, recorded as customer debt.</li>
 * </ul>
 */
public final class CartTotals {

    private static final CartTotals ZERO =
            new CartTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO);

    private final BigDecimal subtotal;
    private final BigDecimal discount;
    private final BigDecimal total;
    private final BigDecimal amountPaid;
    private final BigDecimal amountDue;

    public CartTotals(@NonNull BigDecimal subtotal,
                      @NonNull BigDecimal discount,
                      @NonNull BigDecimal total,
                      @NonNull BigDecimal amountPaid,
                      @NonNull BigDecimal amountDue) {
        this.subtotal = subtotal;
        this.discount = discount;
        this.total = total;
        this.amountPaid = amountPaid;
        this.amountDue = amountDue;
    }

    @NonNull
    public static CartTotals zero() {
        return ZERO;
    }

    /**
     * @param lineTotals  one entry per line; a null entry means a line still needs a price
     * @param discount    sale-level discount amount (0 when none)
     * @param amountPaid  what the customer hands over
     */
    @NonNull
    public static CartTotals of(@NonNull java.util.List<BigDecimal> lineTotals,
                                @NonNull BigDecimal discount,
                                @NonNull BigDecimal amountPaid) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (BigDecimal lineTotal : lineTotals) {
            if (lineTotal != null) {
                subtotal = subtotal.add(lineTotal);
            }
        }
        BigDecimal appliedDiscount = discount.max(BigDecimal.ZERO);
        if (appliedDiscount.compareTo(subtotal) > 0) {
            // Never let the UI show a negative total; the server rejects discount > subtotal
            // with 400 INVALID_SALE, and clamping keeps the preview honest.
            appliedDiscount = subtotal;
        }
        BigDecimal total = subtotal.subtract(appliedDiscount).max(BigDecimal.ZERO);
        BigDecimal paid = amountPaid.max(BigDecimal.ZERO);
        if (paid.compareTo(total) > 0) {
            paid = total;
        }
        return new CartTotals(subtotal, appliedDiscount, total, paid, total.subtract(paid));
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

    public boolean isPaidInFull() {
        return amountDue.signum() == 0;
    }
}