package com.lpms.domain.cart;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.data.dto.DrugResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * One cart line.
 *
 * <p>Pricing follows the backend contract exactly:</p>
 * <ul>
 *   <li>the line starts at the drug's default {@code sellingPrice};</li>
 *   <li>the cashier may override it per line — the override is what gets sent;</li>
 *   <li>when the drug has <b>no</b> default price and no override, the line is
 *       incomplete and the server would reject the sale with 400 {@code INVALID_SALE},
 *       so {@link #isPriceMissing()} blocks checkout client-side.</li>
 * </ul>
 *
 * <p>All money is {@link BigDecimal}; no double ever touches a price.</p>
 */
public final class CartLine {

    private final long drugId;
    private final String drugName;
    private final String unit;

    /** The drug's default selling price; null when it was never set. */
    @Nullable
    private final BigDecimal defaultPrice;

    private final int stockAvailable;

    private int quantity;

    /** Cashier's per-line override; null means "use the drug default". */
    @Nullable
    private BigDecimal priceOverride;

    private final boolean active;

    public CartLine(@NonNull DrugResponse drug, int quantity) {
        this(drug.getId() == null ? 0L : drug.getId(),
                drug.displayName(),
                drug.getUnit(),
                drug.getSellingPrice(),
                drug.getCurrentQuantity(),
                quantity,
                null,
                drug.isActive());
    }

    public CartLine(long drugId,
                    @NonNull String drugName,
                    @Nullable String unit,
                    @Nullable BigDecimal defaultPrice,
                    int stockAvailable,
                    int quantity,
                    @Nullable BigDecimal priceOverride,
                    boolean active) {
        this.drugId = drugId;
        this.drugName = drugName;
        this.unit = unit;
        this.defaultPrice = defaultPrice;
        this.stockAvailable = stockAvailable;
        this.quantity = quantity;
        this.priceOverride = priceOverride;
        this.active = active;
    }

    public long getDrugId() {
        return drugId;
    }

    @NonNull
    public String getDrugName() {
        return drugName;
    }

    @Nullable
    public String getUnit() {
        return unit;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getStockAvailable() {
        return stockAvailable;
    }

    @Nullable
    public BigDecimal getDefaultPrice() {
        return defaultPrice;
    }

    @Nullable
    public BigDecimal getPriceOverride() {
        return priceOverride;
    }

    public boolean isActive() {
        return active;
    }

    /** True when the cashier typed a different price than the drug default. */
    public boolean hasOverride() {
        return priceOverride != null
                && (defaultPrice == null || priceOverride.compareTo(defaultPrice) != 0);
    }

    /** Price actually charged for this line, or null when it still has to be typed. */
    @Nullable
    public BigDecimal effectivePrice() {
        return priceOverride != null ? priceOverride : defaultPrice;
    }

    /** True when neither an override nor a default price exists. */
    public boolean isPriceMissing() {
        return effectivePrice() == null;
    }

    /** {@code quantity × effectivePrice}, null while the price is missing. */
    @Nullable
    public BigDecimal lineTotal() {
        BigDecimal price = effectivePrice();
        if (price == null) {
            return null;
        }
        return price.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    /** Quantity above available stock: the server will answer INSUFFICIENT_STOCK. */
    public boolean exceedsStock() {
        return quantity > stockAvailable;
    }

    /** An inactive drug cannot be sold: the server answers 400 INVALID_SALE. */
    public boolean isSellable() {
        return active && quantity > 0 && !isPriceMissing() && !exceedsStock();
    }

    @NonNull
    public CartLine withQuantity(int newQuantity) {
        CartLine copy = new CartLine(drugId, drugName, unit, defaultPrice, stockAvailable,
                Math.max(0, newQuantity), priceOverride, active);
        return copy;
    }

    /**
     * @param price new override, or null to fall back to the drug default
     */
    @NonNull
    public CartLine withPriceOverride(@Nullable BigDecimal price) {
        return new CartLine(drugId, drugName, unit, defaultPrice, stockAvailable, quantity,
                price, active);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CartLine)) {
            return false;
        }
        CartLine other = (CartLine) o;
        return drugId == other.drugId
                && quantity == other.quantity
                && Objects.equals(defaultPrice, other.defaultPrice)
                && Objects.equals(priceOverride, other.priceOverride);
    }

    @Override
    public int hashCode() {
        return Objects.hash(drugId, quantity, defaultPrice, priceOverride);
    }

    @NonNull
    @Override
    public String toString() {
        return "CartLine{" + drugName + " x" + quantity + " @ " + effectivePrice() + "}";
    }
}