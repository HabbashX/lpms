package com.lpms.local.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.math.BigDecimal;

/**
 * One line of an offline sale.
 *
 * <p>{@code unitCost} is frozen at the moment of sale, so a later stock receipt cannot
 * rewrite the profit already recorded - the backend stores the same value immutably.</p>
 */
@Entity(tableName = "sale_items",
        foreignKeys = @ForeignKey(
                entity = SaleEntity.class,
                parentColumns = "id",
                childColumns = "sale_id",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("sale_id"), @Index("drug_id")})
public final class SaleItemEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    @ColumnInfo(name = "sale_id")
    private final long saleId;

    @ColumnInfo(name = "drug_id")
    private final long drugId;

    @NonNull
    @ColumnInfo(name = "drug_name")
    private final String drugName;

    @ColumnInfo(name = "quantity")
    private final int quantity;

    @NonNull
    @ColumnInfo(name = "unit_selling_price")
    private final BigDecimal unitSellingPrice;

    @NonNull
    @ColumnInfo(name = "unit_cost")
    private final BigDecimal unitCost;

    /** quantity x unitSellingPrice. */
    @NonNull
    @ColumnInfo(name = "line_total")
    private final BigDecimal lineTotal;

    @NonNull
    @ColumnInfo(name = "profit")
    private final BigDecimal profit;

    public SaleItemEntity(long id,
                          long saleId,
                          long drugId,
                          @NonNull String drugName,
                          int quantity,
                          @NonNull BigDecimal unitSellingPrice,
                          @NonNull BigDecimal unitCost,
                          @NonNull BigDecimal lineTotal,
                          @NonNull BigDecimal profit) {
        this.id = id;
        this.saleId = saleId;
        this.drugId = drugId;
        this.drugName = drugName;
        this.quantity = quantity;
        this.unitSellingPrice = unitSellingPrice;
        this.unitCost = unitCost;
        this.lineTotal = lineTotal;
        this.profit = profit;
    }

    public long getId() {
        return id;
    }

    public long getSaleId() {
        return saleId;
    }

    public long getDrugId() {
        return drugId;
    }

    @NonNull
    public String getDrugName() {
        return drugName;
    }

    public int getQuantity() {
        return quantity;
    }

    @NonNull
    public BigDecimal getUnitSellingPrice() {
        return unitSellingPrice;
    }

    @NonNull
    public BigDecimal getUnitCost() {
        return unitCost;
    }

    @NonNull
    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    @NonNull
    public BigDecimal getProfit() {
        return profit;
    }
}
