package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;

/**
 * {@code PUT /drugs/{id}} body (ADMIN/PHARMACIST): identical to
 * {@link CreateDrugRequest} plus {@code active}.
 *
 * <p>This is also the call used by the drug-pricing screen's
 * "Apply as selling price": send the whole drug with the new {@code sellingPrice}
 * so no other field is lost.</p>
 */
public final class UpdateDrugRequest {

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

    @SerializedName("active")
    private final Boolean active;

    public UpdateDrugRequest(@Nullable String name,
                             @Nullable String genericName,
                             @Nullable String barcode,
                             @Nullable String manufacturer,
                             @Nullable Long categoryId,
                             @Nullable String dosageForm,
                             @Nullable String strength,
                             @Nullable String unit,
                             @Nullable BigDecimal sellingPrice,
                             @Nullable String description,
                             @Nullable Integer minimumStockLevel,
                             @Nullable Boolean active) {
        this.name = name;
        this.genericName = genericName;
        this.barcode = barcode;
        this.manufacturer = manufacturer;
        this.categoryId = categoryId;
        this.dosageForm = dosageForm;
        this.strength = strength;
        this.unit = unit;
        this.sellingPrice = sellingPrice;
        this.description = description;
        this.minimumStockLevel = minimumStockLevel;
        this.active = active;
    }

    /** Builds a full update from a fetched drug, replacing only the selling price. */
    @NonNull
    public static UpdateDrugRequest withSellingPrice(@NonNull DrugResponse drug,
                                                     @NonNull BigDecimal sellingPrice) {
        return new UpdateDrugRequest(
                drug.getName(),
                drug.getGenericName(),
                drug.getBarcode(),
                drug.getManufacturer(),
                drug.getCategoryId(),
                drug.getDosageForm(),
                drug.getStrength(),
                drug.getUnit(),
                sellingPrice,
                drug.getDescription(),
                drug.getMinimumStockLevel(),
                drug.isActive());
    }

    /** Builds a full update from a fetched drug, replacing only the active flag. */
    @NonNull
    public static UpdateDrugRequest withActive(@NonNull DrugResponse drug, boolean active) {
        return new UpdateDrugRequest(
                drug.getName(),
                drug.getGenericName(),
                drug.getBarcode(),
                drug.getManufacturer(),
                drug.getCategoryId(),
                drug.getDosageForm(),
                drug.getStrength(),
                drug.getUnit(),
                drug.getSellingPrice(),
                drug.getDescription(),
                drug.getMinimumStockLevel(),
                active);
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

    @Nullable
    public Integer getMinimumStockLevel() {
        return minimumStockLevel;
    }

    @Nullable
    public Boolean getActive() {
        return active;
    }
}