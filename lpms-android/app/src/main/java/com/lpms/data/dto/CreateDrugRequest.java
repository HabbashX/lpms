package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;

/**
 * {@code POST /drugs} body (ADMIN/PHARMACIST).
 *
 * <p>Limits enforced by the server: name required ≤150, genericName ≤150,
 * barcode ≤50, manufacturer ≤150, strength ≤50, unit ≤30, description ≤500,
 * sellingPrice &gt; 0, minimumStockLevel ≥ 0.</p>
 *
 * <p><b>Always send {@code categoryId}</b> (from {@code GET /categories}). The
 * {@code category} free-text field is a legacy fallback that the server ignores when
 * {@code categoryId} is present, so the client never populates it.</p>
 *
 * <p>A duplicate barcode is rejected with 409 {@code BARCODE_ALREADY_EXISTS}; an
 * unknown {@code categoryId} with 404 {@code NOT_FOUND}.</p>
 */
public final class CreateDrugRequest {

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

    public CreateDrugRequest(@Nullable String name,
                             @Nullable String genericName,
                             @Nullable String barcode,
                             @Nullable String manufacturer,
                             @Nullable Long categoryId,
                             @Nullable String dosageForm,
                             @Nullable String strength,
                             @Nullable String unit,
                             @Nullable BigDecimal sellingPrice,
                             @Nullable String description,
                             @Nullable Integer minimumStockLevel) {
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
}