package com.lpms.ui.inventory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.lpms.data.dto.ExpiringBatchResponse;
import com.lpms.data.dto.LowStockDrugResponse;
import com.lpms.data.dto.StockBatchResponse;
import com.lpms.data.dto.DrugValuationResponse;

import java.math.BigDecimal;

/**
 * One row shape for the four stock views.
 *
 * <p>Batches, low stock, expiring batches and valuation are four different payloads over
 * the same question - "what do we hold, and is it any good" - so they share a screen and a
 * row layout instead of becoming four near-identical screens. Each mode contributes only
 * the text it can actually fill in, and leaves the rest null for the binder to hide.</p>
 */
public final class InventoryRow {

    /** Which of the four endpoints produced this row. */
    public enum Kind {
        BATCH,
        LOW_STOCK,
        EXPIRING,
        VALUATION
    }

    @NonNull
    public final Kind kind;

    /** Stable identity for DiffUtil; never null. */
    @NonNull
    public final String key;

    @Nullable
    public final StockBatchResponse batch;
    @Nullable
    public final LowStockDrugResponse lowStock;
    @Nullable
    public final ExpiringBatchResponse expiring;
    @Nullable
    public final DrugValuationResponse valuation;

    /** Set only when a stock level is worth flagging, so the badge can be hidden otherwise. */
    @Nullable
    public final Alert alert;

    public enum Alert {
        LOW_STOCK,
        EXPIRED,
        EXPIRING_SOON
    }

    private InventoryRow(@NonNull Kind kind,
                         @NonNull String key,
                         @Nullable StockBatchResponse batch,
                         @Nullable LowStockDrugResponse lowStock,
                         @Nullable ExpiringBatchResponse expiring,
                         @Nullable DrugValuationResponse valuation,
                         @Nullable Alert alert) {
        this.kind = kind;
        this.key = key;
        this.batch = batch;
        this.lowStock = lowStock;
        this.expiring = expiring;
        this.valuation = valuation;
        this.alert = alert;
    }

    @NonNull
    public static InventoryRow of(@NonNull StockBatchResponse batch, @Nullable Alert alert) {
        return new InventoryRow(Kind.BATCH, "batch:" + batch.getId(), batch, null, null, null,
                alert);
    }

    @NonNull
    public static InventoryRow of(@NonNull LowStockDrugResponse drug) {
        // A low-stock list is entirely alerts, so the row does not need the badge as well.
        return new InventoryRow(Kind.LOW_STOCK, "low:" + drug.getDrugId(), null, drug, null, null,
                Alert.LOW_STOCK);
    }

    @NonNull
    public static InventoryRow of(@NonNull ExpiringBatchResponse batch, @Nullable Alert alert) {
        return new InventoryRow(Kind.EXPIRING, "expiring:" + batch.getBatchId(), null, null, batch,
                null, alert);
    }

    @NonNull
    public static InventoryRow of(@NonNull DrugValuationResponse valuation) {
        return new InventoryRow(Kind.VALUATION, "valuation:" + valuation.getDrugId(), null, null,
                null, valuation, null);
    }

    /** Drug id, when the payload carries one. Used to open the drug detail screen. */
    @Nullable
    public Long drugId() {
        if (batch != null) {
            return batch.getDrugId();
        }
        if (lowStock != null) {
            return lowStock.getDrugId();
        }
        if (expiring != null) {
            return expiring.getDrugId();
        }
        if (valuation != null) {
            return valuation.getDrugId();
        }
        return null;
    }

    /** Only the valuation view has a money figure worth showing on the right. */
    @Nullable
    public BigDecimal highlightValue() {
        return valuation == null ? null : valuation.getTotalInventoryCost();
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InventoryRow)) {
            return false;
        }
        InventoryRow that = (InventoryRow) other;
        return key.equals(that.key) && alert == that.alert;
    }

    @Override
    public int hashCode() {
        return key.hashCode() * 31 + (alert == null ? 0 : alert.hashCode());
    }

    /** Only the batch and valuation rows navigate anywhere. */
    public boolean isClickable() {
        return kind == Kind.BATCH || kind == Kind.VALUATION || kind == Kind.LOW_STOCK;
    }

    /** Marker so the adapter can be constructed once and reused across modes. */
    public interface RowAdapter {
        void onBind(@NonNull RecyclerView.ViewHolder holder, @NonNull InventoryRow row);
    }
}