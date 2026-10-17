package com.lpms.local.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.math.BigDecimal;

/**
 * A stock batch, kept because the weighted-average cost is derived from batches and not
 * from drugs. Receiving stock offline appends a row and refreshes the drug's cost.
 */
@Entity(tableName = "stock_batches")
public final class StockBatchEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    @ColumnInfo(name = "drug_id")
    private final long drugId;

    @ColumnInfo(name = "remaining_quantity")
    private final int remainingQuantity;

    @NonNull
    @ColumnInfo(name = "unit_purchase_price")
    private final BigDecimal unitPurchasePrice;

    @Nullable
    @ColumnInfo(name = "supplier")
    private final String supplier;

    @Nullable
    @ColumnInfo(name = "batch_number")
    private final String batchNumber;

    @Nullable
    @ColumnInfo(name = "expiration_date")
    private final String expirationDate;

    @NonNull
    @ColumnInfo(name = "received_at")
    private final String receivedAt;

    public StockBatchEntity(long id,
                            long drugId,
                            int remainingQuantity,
                            @NonNull BigDecimal unitPurchasePrice,
                            @Nullable String supplier,
                            @Nullable String batchNumber,
                            @Nullable String expirationDate,
                            @NonNull String receivedAt) {
        this.id = id;
        this.drugId = drugId;
        this.remainingQuantity = remainingQuantity;
        this.unitPurchasePrice = unitPurchasePrice;
        this.supplier = supplier;
        this.batchNumber = batchNumber;
        this.expirationDate = expirationDate;
        this.receivedAt = receivedAt;
    }

    public long getId() {
        return id;
    }

    public long getDrugId() {
        return drugId;
    }

    public int getRemainingQuantity() {
        return remainingQuantity;
    }

    @NonNull
    public BigDecimal getUnitPurchasePrice() {
        return unitPurchasePrice;
    }

    @Nullable
    public String getSupplier() {
        return supplier;
    }

    @Nullable
    public String getBatchNumber() {
        return batchNumber;
    }

    @Nullable
    public String getExpirationDate() {
        return expirationDate;
    }

    @NonNull
    public String getReceivedAt() {
        return receivedAt;
    }
}
