package com.lpms.ui.inventory;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.lpms.R;
import com.lpms.core.util.Dates;
import com.lpms.core.util.Money;
import com.lpms.data.dto.ExpiringBatchResponse;
import com.lpms.data.dto.LowStockDrugResponse;
import com.lpms.data.dto.StockBatchResponse;
import com.lpms.data.dto.DrugValuationResponse;
import com.lpms.databinding.ItemInventoryBinding;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Rows for the four stock views.
 *
 * <p>Only the valuation view carries money, and only batches carry a cost per unit, so a
 * money column appears where the payload supports it and is hidden elsewhere rather than
 * showing a zero.</p>
 */
public final class InventoryAdapter extends ListAdapter<InventoryRow, InventoryAdapter.ViewHolder> {

    public interface Listener {
        /** Tapping a row opens the drug it belongs to. */
        void onDrugClicked(@NonNull InventoryRow row, long drugId);
    }

    private static final DiffUtil.ItemCallback<InventoryRow> DIFF =
            new DiffUtil.ItemCallback<InventoryRow>() {
                @Override
                public boolean areItemsTheSame(@NonNull InventoryRow oldItem,
                                               @NonNull InventoryRow newItem) {
                    return oldItem.key.equals(newItem.key);
                }

                @Override
                public boolean areContentsTheSame(@NonNull InventoryRow oldItem,
                                                  @NonNull InventoryRow newItem) {
                    // remaining quantity, severity and cost all change as stock moves, and
                    // all three are on screen.
                    return Objects.equals(oldItem.alert, newItem.alert)
                            && Objects.equals(oldItem.highlightValue(),
                            newItem.highlightValue())
                            && Objects.equals(summaryOf(oldItem), summaryOf(newItem));
                }

                @Nullable
                private String summaryOf(@NonNull InventoryRow row) {
                    if (row.batch != null) {
                        return row.batch.getRemainingQuantity() + "|"
                                + row.batch.getUnitPurchasePrice();
                    }
                    if (row.expiring != null) {
                        return row.expiring.getRemainingQuantity() + "|"
                                + row.expiring.getDaysUntilExpiration();
                    }
                    if (row.lowStock != null) {
                        return row.lowStock.getCurrentQuantity() + "|" + row.lowStock.getUnit();
                    }
                    if (row.valuation != null) {
                        return row.valuation.getTotalQuantity() + "|"
                                + row.valuation.getWeightedAverageCost();
                    }
                    return null;
                }
            };

    private final Listener listener;
    private final String currencySymbol;

    public InventoryAdapter(@NonNull Listener listener, @NonNull String currencySymbol) {
        super(DIFF);
        this.listener = listener;
        this.currencySymbol = currencySymbol;
        setHasStableIds(false);
    }

    @NonNull
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InventoryRow row = getItem(position);
        if (row == null) {
            holder.clear();
            return;
        }
        holder.bind(row, listener, currencySymbol);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_inventory, parent, false));
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemInventoryBinding binding;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.binding = ItemInventoryBinding.bind(itemView);
        }

        void clear() {
            binding.title.setText("");
            binding.subtitle.setText("");
            binding.detail.setText("");
            binding.value.setText("");
            binding.badge.setVisibility(View.GONE);
            binding.detail.setVisibility(View.GONE);
            binding.value.setVisibility(View.GONE);
        }

        void bind(@NonNull InventoryRow row, @NonNull Listener listener,
                  @NonNull String currency) {
            switch (row.kind) {
                case BATCH:
                    bindBatch(row, listener, currency);
                    break;
                case LOW_STOCK:
                    bindLowStock(row, listener);
                    break;
                case EXPIRING:
                    bindExpiring(row, listener);
                    break;
                case VALUATION:
                default:
                    bindValuation(row, listener, currency);
                    break;
            }
            bindBadge(row);
        }

        private void bindBatch(@NonNull InventoryRow row, @NonNull Listener listener,
                               @NonNull String currency) {
            StockBatchResponse batch = row.batch;
            binding.title.setText(nullToEmpty(batch.getDrugName()));
            binding.subtitle.setText(batchSummary(batch));
            binding.detail.setVisibility(View.GONE);
            hideValue();
            bindClick(row, listener);
        }

        private void bindLowStock(@NonNull InventoryRow row, @NonNull Listener listener) {
            LowStockDrugResponse drug = row.lowStock;
            binding.title.setText(nullToEmpty(drug.getName()));
            binding.subtitle.setText(binding.getRoot().getContext().getString(
                    R.string.inventory_low_stock_detail,
                    drug.getCurrentQuantity(),
                    drug.getMinimumStockLevel(),
                    nullToEmpty(drug.getUnit())));
            binding.detail.setVisibility(View.GONE);
            hideValue();
            bindClick(row, listener);
        }

        private void bindExpiring(@NonNull InventoryRow row, @NonNull Listener listener) {
            ExpiringBatchResponse batch = row.expiring;
            binding.title.setText(nullToEmpty(batch.getDrugName()));
            String batchLabel = batch.getBatchNumber() == null
                    || batch.getBatchNumber().trim().isEmpty()
                    ? binding.getRoot().getContext().getString(R.string.pricing_batch_unnamed)
                    : batch.getBatchNumber().trim();
            binding.subtitle.setText(binding.getRoot().getContext().getString(
                    R.string.inventory_expiring_detail,
                    batchLabel,
                    batch.getRemainingQuantity(),
                    Dates.shortDate(binding.getRoot().getContext(), batch.getExpirationDate())));
            binding.detail.setVisibility(View.GONE);
            hideValue();
            bindClick(row, listener);
        }

        private void bindValuation(@NonNull InventoryRow row, @NonNull Listener listener,
                                   @NonNull String currency) {
            DrugValuationResponse valuation = row.valuation;
            binding.title.setText(nullToEmpty(valuation.getDrugName()));
            binding.subtitle.setText(binding.getRoot().getContext().getString(
                    R.string.inventory_valuation_detail,
                    valuation.getTotalQuantity(),
                    Money.format(valuation.getWeightedAverageCost(), currency)));
            // This view has a real cost per unit to show, unlike the others.
            binding.detail.setVisibility(View.VISIBLE);
            binding.detail.setText(binding.getRoot().getContext().getString(
                    R.string.inventory_average_cost,
                    Money.format(valuation.getWeightedAverageCost(), currency)));

            BigDecimal total = valuation.getTotalInventoryCost();
            if (total == null) {
                hideValue();
            } else {
                binding.value.setText(Money.format(total, currency));
                binding.value.setVisibility(View.VISIBLE);
            }
            bindClick(row, listener);
        }

        private void bindBadge(@NonNull InventoryRow row) {
            InventoryRow.Alert alert = row.alert;
            if (alert == null) {
                binding.badge.setVisibility(View.GONE);
                return;
            }
            String label;
            int background;
            switch (alert) {
                case LOW_STOCK:
                    label = binding.getRoot().getContext().getString(R.string.drug_low_stock);
                    background = R.drawable.bg_badge_low;
                    break;
                case EXPIRED:
                    label = binding.getRoot().getContext().getString(R.string.stock_expired);
                    background = R.drawable.bg_badge_inactive;
                    break;
                case EXPIRING_SOON:
                default:
                    label = binding.getRoot().getContext()
                            .getString(R.string.stock_expiring_soon);
                    background = R.drawable.bg_badge_low;
                    break;
            }
            binding.badge.setText(label);
            binding.badge.setBackgroundResource(background);
            binding.badge.setVisibility(View.VISIBLE);
        }

        private void bindClick(@NonNull InventoryRow row, @NonNull Listener listener) {
            Long drugId = row.drugId();
            if (drugId == null || !row.isClickable()) {
                itemView.setOnClickListener(null);
                itemView.setClickable(false);
                return;
            }
            itemView.setClickable(true);
            itemView.setOnClickListener(v -> listener.onDrugClicked(row, drugId));
        }

        private void hideValue() {
            binding.value.setText("");
            binding.value.setVisibility(View.GONE);
        }

        @NonNull
        private String batchSummary(@NonNull StockBatchResponse batch) {
            String batchLabel = batch.getBatchNumber() == null
                    || batch.getBatchNumber().trim().isEmpty()
                    ? binding.getRoot().getContext().getString(R.string.pricing_batch_unnamed)
                    : batch.getBatchNumber().trim();
            String expiry = batch.getExpirationDate() == null
                    ? binding.getRoot().getContext().getString(R.string.pricing_batch_no_expiry)
                    : Dates.shortDate(binding.getRoot().getContext(), batch.getExpirationDate());
            return binding.getRoot().getContext().getString(R.string.inventory_batch_detail,
                    batchLabel, batch.getRemainingQuantity(), expiry);
        }

        @NonNull
        private static String nullToEmpty(@Nullable String value) {
            return value == null ? "" : value;
        }
    }
}