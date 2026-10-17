package com.lpms.local.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.math.BigDecimal;

/**
 * A drug as stored for offline mode.
 *
 * <p>Field names mirror {@link com.lpms.data.dto.DrugResponse} so the local backend can
 * build one from the other without a translation layer.</p>
 *
 * <p>Cost fields are deliberately kept: the app hides them from employees in the UI, and
 * a preview that could not compute profit would look broken to anyone demonstrating the
 * admin view.</p>
 */
@Entity(tableName = "drugs")
public final class DrugEntity {

    @PrimaryKey
    @ColumnInfo(name = "id")
    private final long id;

    @NonNull
    @ColumnInfo(name = "name")
    private final String name;

    @Nullable
    @ColumnInfo(name = "generic_name")
    private final String genericName;

    @Nullable
    @ColumnInfo(name = "barcode")
    private final String barcode;

    @Nullable
    @ColumnInfo(name = "category_id")
    private final Long categoryId;

    @Nullable
    @ColumnInfo(name = "category_name")
    private final String categoryName;

    @Nullable
    @ColumnInfo(name = "dosage_form")
    private final String dosageForm;

    @Nullable
    @ColumnInfo(name = "strength")
    private final String strength;

    @Nullable
    @ColumnInfo(name = "package_size")
    private final String packageSize;

    /** Drug's default selling price; null means a line must override it. */
    @Nullable
    @ColumnInfo(name = "selling_price")
    private final BigDecimal sellingPrice;

    /** Weighted-average unit cost, recomputed as batches are received offline. */
    @Nullable
    @ColumnInfo(name = "average_cost")
    private final BigDecimal averageCost;

    @ColumnInfo(name = "current_quantity")
    private final int currentQuantity;

    @ColumnInfo(name = "min_quantity")
    private final int minQuantity;

    @ColumnInfo(name = "active")
    private final boolean active;

    public DrugEntity(long id,
                      @NonNull String name,
                      @Nullable String genericName,
                      @Nullable String barcode,
                      @Nullable Long categoryId,
                      @Nullable String categoryName,
                      @Nullable String dosageForm,
                      @Nullable String strength,
                      @Nullable String packageSize,
                      @Nullable BigDecimal sellingPrice,
                      @Nullable BigDecimal averageCost,
                      int currentQuantity,
                      int minQuantity,
                      boolean active) {
        this.id = id;
        this.name = name;
        this.genericName = genericName;
        this.barcode = barcode;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.dosageForm = dosageForm;
        this.strength = strength;
        this.packageSize = packageSize;
        this.sellingPrice = sellingPrice;
        this.averageCost = averageCost;
        this.currentQuantity = currentQuantity;
        this.minQuantity = minQuantity;
        this.active = active;
    }

    public long getId() {
        return id;
    }

    @NonNull
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
    public Long getCategoryId() {
        return categoryId;
    }

    @Nullable
    public String getCategoryName() {
        return categoryName;
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
    public String getPackageSize() {
        return packageSize;
    }

    @Nullable
    public BigDecimal getSellingPrice() {
        return sellingPrice;
    }

    @Nullable
    public BigDecimal getAverageCost() {
        return averageCost;
    }

    public int getCurrentQuantity() {
        return currentQuantity;
    }

    public int getMinQuantity() {
        return minQuantity;
    }

    public boolean isActive() {
        return active;
    }
}
