package com.lpms.ui.pos;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.lpms.R;
import com.lpms.core.util.Money;
import com.lpms.databinding.ItemCartLineBinding;
import com.lpms.domain.cart.CartLine;

import java.math.BigDecimal;

/**
 * Cart rows: quantity stepper, per-line price override and a line total.
 *
 * <p>Lines whose price is missing, that exceed stock, or that belong to an inactive drug
 * show an inline warning, because each of those is rejected server-side.</p>
 */
public final class CartAdapter extends ListAdapter<CartLine, CartAdapter.ViewHolder> {

    /** Row interactions. */
    public interface Listener {
        void onQuantityChanged(@NonNull CartLine line, int newQuantity);

        /** @param priceText raw field content; null/blank restores the drug default */
        void onPriceChanged(@NonNull CartLine line, @Nullable String priceText);

        void onRemove(@NonNull CartLine line);
    }

    private final Listener listener;
    private final String currencySymbol;

    public CartAdapter(@NonNull Listener listener, @NonNull String currencySymbol) {
        super(DIFF);
        this.listener = listener;
        this.currencySymbol = currencySymbol;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemCartLineBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), listener, currencySymbol);
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemCartLineBinding binding;

        ViewHolder(@NonNull ItemCartLineBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull CartLine line,
                  @NonNull Listener listener,
                  @NonNull String currencySymbol) {
            binding.name.setText(line.getDrugName());
            binding.quantity.setText(String.valueOf(line.getQuantity()));

            StringBuilder stockText = new StringBuilder(itemView.getContext().getString(
                    R.string.pos_available, line.getStockAvailable()));
            if (line.getUnit() != null && !line.getUnit().trim().isEmpty()) {
                stockText.append(' ').append(line.getUnit().trim());
            }
            binding.stock.setText(stockText.toString());

            // Only write the price field from the model; the field is left blank while
            // untouched so an override reads as an explicit cashier decision.
            binding.price.setText(line.getPriceOverride() == null
                    ? "" : Money.toPlainString(line.getPriceOverride()));

            BigDecimal lineTotal = line.lineTotal();
            binding.lineTotal.setText(lineTotal == null
                    ? itemView.getContext().getString(R.string.pos_price_required_short)
                    : Money.format(lineTotal, currencySymbol));

            binding.decrease.setOnClickListener(v ->
                    listener.onQuantityChanged(line, line.getQuantity() - 1));
            binding.increase.setOnClickListener(v ->
                    listener.onQuantityChanged(line, line.getQuantity() + 1));
            binding.remove.setOnClickListener(v -> listener.onRemove(line));

            binding.price.setOnFocusChangeListener((v, focused) -> {
                if (!focused) {
                    listener.onPriceChanged(line, text(binding.price));
                }
            });
            binding.price.setOnEditorActionListener((v, actionId, event) -> {
                listener.onPriceChanged(line, text(binding.price));
                binding.price.clearFocus();
                return true;
            });

            String warning = warningFor(line);
            binding.warning.setVisibility(warning == null ? View.GONE : View.VISIBLE);
            binding.warning.setText(warning == null ? "" : warning);
        }

        @Nullable
        private String warningFor(@NonNull CartLine line) {
            Context context = itemView.getContext();
            if (!line.isActive()) {
                return context.getString(R.string.pos_line_inactive);
            }
            if (line.isPriceMissing()) {
                return context.getString(R.string.pos_price_required);
            }
            if (line.exceedsStock()) {
                return context.getString(R.string.pos_line_stock,
                        line.getQuantity(), line.getStockAvailable());
            }
            return null;
        }

        @NonNull
        private static String text(@NonNull com.google.android.material.textfield.TextInputEditText f) {
            return f.getText() == null ? "" : f.getText().toString().trim();
        }
    }

    private static final DiffUtil.ItemCallback<CartLine> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull CartLine oldItem, @NonNull CartLine newItem) {
            return oldItem.getDrugId() == newItem.getDrugId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull CartLine oldItem, @NonNull CartLine newItem) {
            return oldItem.equals(newItem);
        }
    };
}