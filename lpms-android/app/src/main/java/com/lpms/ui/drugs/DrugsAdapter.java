package com.lpms.ui.drugs;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.lpms.R;
import com.lpms.core.util.Money;
import com.lpms.data.dto.DrugResponse;
import com.lpms.databinding.ItemDrugBinding;

/**
 * Drug rows backed by Paging 3 ({@link PagingDataAdapter} + {@link DiffUtil}).
 *
 * <p>Rows show name/generic name, stock vs. minimum level and the default selling
 * price. Cost figures are not part of {@code DrugResponse}, so nothing here can leak
 * them; per-batch cost lives on the pricing screen, which is role-gated.</p>
 */
public final class DrugsAdapter extends ListAdapter<DrugResponse, DrugsAdapter.ViewHolder> {

    /** Row interaction, implemented by the hosting Fragment. */
    public interface Listener {
        void onDrugClicked(@NonNull DrugResponse drug);

        /** Sends the id; the Fragment resolves the full drug for the detail screen. */
        void onDrugMenuClicked(@NonNull DrugResponse drug, @NonNull android.view.View anchor);
    }

    private final Listener listener;
    private final String currencySymbol;

    public DrugsAdapter(@NonNull Listener listener, @NonNull String currencySymbol) {
        super(DIFF);
        this.listener = listener;
        this.currencySymbol = currencySymbol;
        setHasStableIds(false);
    }


    @NonNull
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DrugResponse drug = getItem(position);
        if (drug == null) {
            // Placeholder slot; Paging is configured without placeholders, so this only
            // happens for a null item during a generation swap.
            holder.clear();
            return;
        }
        holder.bind(drug, listener, currencySymbol);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_drug, parent, false);
        return new ViewHolder(view);
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemDrugBinding binding;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.binding = ItemDrugBinding.bind(itemView);
        }

        void clear() {
            binding.name.setText("");
            binding.subtitle.setText("");
            binding.price.setText("");
            binding.stock.setText("");
            binding.badge.setVisibility(View.GONE);
        }

        void bind(@NonNull DrugResponse drug,
                  @NonNull Listener listener,
                  @NonNull String currencySymbol) {
            binding.name.setText(drug.displayName());

            String generic = drug.getGenericName();
            String category = drug.getCategory();
            StringBuilder subtitle = new StringBuilder();
            if (generic != null && !generic.trim().isEmpty()) {
                subtitle.append(generic.trim());
            }
            if (category != null && !category.trim().isEmpty()) {
                if (subtitle.length() > 0) {
                    subtitle.append(" · ");
                }
                subtitle.append(category.trim());
            }
            binding.subtitle.setText(subtitle);
            binding.subtitle.setVisibility(subtitle.length() == 0 ? View.GONE : View.VISIBLE);

            if (drug.hasSellingPrice()) {
                binding.price.setText(Money.format(drug.getSellingPrice(), currencySymbol));
                binding.price.setVisibility(View.VISIBLE);
            } else {
                // Null sellingPrice: the POS will require a price typed per sale.
                binding.price.setText(R.string.drug_price_not_set);
                binding.price.setVisibility(View.VISIBLE);
            }

            int quantity = drug.getCurrentQuantity();
            int minimum = drug.getMinimumStockLevel();
            binding.stock.setText(itemView.getContext()
                    .getString(R.string.drug_stock_format, quantity, minimum));

            boolean inactive = !drug.isActive();
            boolean low = drug.isLowStock() && drug.isActive();
            if (inactive) {
                binding.badge.setVisibility(View.VISIBLE);
                binding.badge.setText(R.string.drug_inactive);
                binding.badge.setBackgroundResource(R.drawable.bg_badge_inactive);
            } else if (low) {
                binding.badge.setVisibility(View.VISIBLE);
                binding.badge.setText(R.string.drug_low_stock);
                binding.badge.setBackgroundResource(R.drawable.bg_badge_low);
            } else {
                binding.badge.setVisibility(View.GONE);
            }

            binding.getRoot().setOnClickListener(v -> listener.onDrugClicked(drug));
            binding.menu.setOnClickListener(v -> listener.onDrugMenuClicked(drug, v));
        }
    }

    private static final DiffUtil.ItemCallback<DrugResponse> DIFF =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(@NonNull DrugResponse oldItem,
                                               @NonNull DrugResponse newItem) {
                    return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull DrugResponse oldItem,
                                                  @NonNull DrugResponse newItem) {
                    // Everything the row renders: identity fields, price, stock and status.
                    return oldItem.getCurrentQuantity() == newItem.getCurrentQuantity()
                            && oldItem.getMinimumStockLevel() == newItem.getMinimumStockLevel()
                            && oldItem.isActive() == newItem.isActive()
                            && equal(oldItem.getName(), newItem.getName())
                            && equal(oldItem.getGenericName(), newItem.getGenericName())
                            && equal(oldItem.getCategory(), newItem.getCategory())
                            && equal(oldItem.getDosageForm(), newItem.getDosageForm())
                            && equal(oldItem.getStrength(), newItem.getStrength())
                            && equal(oldItem.getSellingPrice(), newItem.getSellingPrice());
                }

                private boolean equal(Object a, Object b) {
                    return a == null ? b == null : a.equals(b);
                }
            };
}