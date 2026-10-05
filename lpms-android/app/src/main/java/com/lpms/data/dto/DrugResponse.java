package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * {@code GET /drugs}, {@code GET /drugs/{id}} and {@code GET /drugs/barcode/{code}}.
 *
 * <p>Note {@link #getSellingPrice()} is nullable: a drug may never have had a
 * default selling price set. The POS pre-fills the cart from it and requires the
 * cashier to type a price when it is null — otherwise the server rejects the sale
 * with 400 {@code INVALID_SALE}.</p>
 */
public final class DrugResponse {

    @SerializedName("id")
    private final Long id;

    @SerializedName("name")
    private final String name;

    @SerializedName("genericName")
    private final String genericName;

    @SerializedName("barcode")
    private final String barcode;

    @SerializedName("manufacturer")
    private final String manufacturer;

    @SerializedName("categoryId")
    private final Long categoryId;

    @SerializedName("category")
    private final String category;

    @SerializedName("dosageForm")
    private final String dosageForm;

    @SerializedName("strength")
    private final String strength;

    @SerializedName("unit")
    private final String unit;

    @SerializedName("sellingPrice")
    private final BigDecimal sellingPrice;

    @SerializedName("description")
    private final String description;

    @SerializedName("minimumStockLevel")
    private final Integer minimumStockLevel;

    @SerializedName("currentQuantity")
    private final Integer currentQuantity;

    @SerializedName("active")
    private final Boolean active;

    @SerializedName("createdAt")
    private final Instant createdAt;

    @SerializedName("updatedAt")
    private final Instant updatedAt;

    public DrugResponse(@Nullable Long id,
                        @Nullable String name,
                        @Nullable String genericName,
                        @Nullable String barcode,
                        @Nullable String manufacturer,
                        @Nullable Long categoryId,
                        @Nullable String category,
                        @Nullable String dosageForm,
                        @Nullable String strength,
                        @Nullable String unit,
                        @Nullable BigDecimal sellingPrice,
                        @Nullable String description,
                        @Nullable Integer minimumStockLevel,
                        @Nullable Integer currentQuantity,
                        @Nullable Boolean active,
                        @Nullable Instant createdAt,
                        @Nullable Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.genericName = genericName;
        this.barcode = barcode;
        this.manufacturer = manufacturer;
        this.categoryId = categoryId;
        this.category = category;
        this.dosageForm = dosageForm;
        this.strength = strength;
        this.unit = unit;
        this.sellingPrice = sellingPrice;
        this.description = description;
        this.minimumStockLevel = minimumStockLevel;
        this.currentQuantity = currentQuantity;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @Nullable
    public Long getId() {
        return id;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @Nullable
    public String getGenericName() {
        return genericName;
    }

    @Nullable
    public String getBarcode() {
        return barcode;
    }

    @Nullable
    public String getManufacturer() {
        return manufacturer;
    }

    @Nullable
    public Long getCategoryId() {
        return categoryId;
    }

    /** Category name as returned by the server, for list rows and receipts. */
    @Nullable
    public String getCategory() {
        return category;
    }

    @Nullable
    public String getDosageForm() {
        return dosageForm;
    }

    @Nullable
    public String getStrength() {
        return strength;
    }

    @Nullable
    public String getUnit() {
        return unit;
    }

    @Nullable
    public BigDecimal getSellingPrice() {
        return sellingPrice;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public int getMinimumStockLevel() {
        return minimumStockLevel == null ? 0 : minimumStockLevel;
    }

    public int getCurrentQuantity() {
        return currentQuantity == null ? 0 : currentQuantity;
    }

    public boolean isActive() {
        return !Boolean.FALSE.equals(active);
    }

    @Nullable
    public Instant getCreatedAt() {
        return createdAt;
    }

    @Nullable
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** True when the POS must ask the cashier for a price on every sale of this drug. */
    public boolean hasSellingPrice() {
        return sellingPrice != null && sellingPrice.signum() > 0;
    }

    public boolean isLowStock() {
        return getCurrentQuantity() <= getMinimumStockLevel();
    }

    /** Label used in lists and the cart: "Amoxcillin 500mg (Capsule)". */
    @NonNull
    public String displayName() {
        StringBuilder sb = new StringBuilder(name == null ? "" : name);
        if (strength != null && !strength.trim().isEmpty()) {
            sb.append(' ').append(strength.trim());
        }
        if (dosageForm != null && !dosageForm.trim().isEmpty()) {
            sb.append(" (").append(dosageForm.trim()).append(')');
        }
        return sb.toString();
    }
}