package com.lpms.ui.pos;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.lpms.R;
import com.lpms.core.util.Money;
import com.lpms.data.dto.DrugResponse;
import com.lpms.databinding.ItemDrugBinding;

/**
 * Type-ahead results for the POS drug field: a tap adds the drug to the cart.
 * Shows stock and price only, since a cashier needs nothing else to sell.
 */
final class DrugsSearchAdapter
        extends ListAdapter<DrugResponse, DrugsSearchAdapter.ViewHolder> {

    interface Listener {
        void onPicked(@NonNull DrugResponse drug);
    }

    private final Listener listener;
    private final String currencySymbol;

    DrugsSearchAdapter(@NonNull Listener listener, @NonNull String currencySymbol) {
        super(DIFF);
        this.listener = listener;
        this.currencySymbol = currencySymbol;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemDrugBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), listener, currencySymbol);
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemDrugBinding binding;

        ViewHolder(@NonNull ItemDrugBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull DrugResponse drug, @NonNull Listener listener, @NonNull String symbol) {
            binding.name.setText(drug.displayName());
            binding.subtitle.setText(drug.getGenericName() == null ? "" : drug.getGenericName());
            binding.stock.setText(itemView.getContext().getString(
                    R.string.pos_available, drug.getCurrentQuantity()));
            binding.price.setText(drug.hasSellingPrice()
                    ? Money.format(drug.getSellingPrice(), symbol)
                    : itemView.getContext().getString(R.string.drug_price_not_set));
            binding.badge.setVisibility(drug.isActive() ? View.GONE : View.VISIBLE);
            binding.badge.setText(R.string.drug_inactive);
            binding.badge.setBackgroundResource(R.drawable.bg_badge_inactive);
            binding.menu.setVisibility(View.GONE);
            binding.getRoot().setOnClickListener(v -> listener.onPicked(drug));
        }
    }

    private static final DiffUtil.ItemCallback<DrugResponse> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull DrugResponse oldItem, @NonNull DrugResponse newItem) {
            return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull DrugResponse oldItem, @NonNull DrugResponse newItem) {
            return oldItem.getCurrentQuantity() == newItem.getCurrentQuantity()
                    && oldItem.isActive() == newItem.isActive()
                    && equal(oldItem.getName(), newItem.getName())
                    && equal(oldItem.getSellingPrice(), newItem.getSellingPrice());
        }

        private boolean equal(Object a, Object b) {
            return a == null ? b == null : a.equals(b);
        }
    };
}