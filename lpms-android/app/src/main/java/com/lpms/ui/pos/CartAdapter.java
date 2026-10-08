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
    /** Captured from onCreateViewHolder so focused rows can be flushed before checkout. */
    @Nullable
    private RecyclerView recyclerView;

    public CartAdapter(@NonNull Listener listener, @NonNull String currencySymbol) {
        super(DIFF);
        this.listener = listener;
        this.currencySymbol = currencySymbol;
    }

    /**
     * Commits the price field of whichever row still holds focus.
     *
     * <p>The watcher already commits on every keystroke, but the final one can still be
     * undispatched when Confirm is tapped. Without this the sale silently falls back to
     * the drug's default price - the worst outcome, because the cashier can see the price
     * they typed while the customer is charged something else.</p>
     */
    public void commitFocusedPrice() {
        if (recyclerView == null) {
            return;
        }
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            RecyclerView.ViewHolder holder =
                    recyclerView.getChildViewHolder(recyclerView.getChildAt(i));
            if (holder instanceof ViewHolder) {
                ((ViewHolder) holder).commitPriceIfFocused(listener);
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (recyclerView == null && parent instanceof RecyclerView) {
            recyclerView = (RecyclerView) parent;
        }
        return new ViewHolder(ItemCartLineBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), listener, currencySymbol);
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemCartLineBinding binding;
        /** The row and listener currently bound, used by the price watcher. */
        @Nullable
        private CartLine boundLine;
        @Nullable
        private Listener boundListener;
        /** Suppresses the watcher while bind() writes the field itself. */
        private boolean suppressWatcher;

        ViewHolder(@NonNull ItemCartLineBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            // Committed as the cashier types rather than on blur. Tapping Confirm straight
            // after typing dispatched the click before focus left the field, so the typed
            // price was discarded and the sale silently fell back to the drug default -
            // the worst outcome, because the cashier can see the price they typed.
            binding.price.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    CartLine line = boundLine;
                    Listener target = boundListener;
                    if (suppressWatcher || line == null || target == null) {
                        return;
                    }
                    target.onPriceChanged(line, text(binding.price));
                }
            });
        }

        void bind(@NonNull CartLine line,
                  @NonNull Listener listener,
                  @NonNull String currencySymbol) {            this.boundLine = line;
            this.boundListener = listener;
            binding.name.setText(line.getDrugName());
            binding.quantity.setText(String.valueOf(line.getQuantity()));

            StringBuilder stockText = new StringBuilder(itemView.getContext().getString(
                    R.string.pos_available, line.getStockAvailable()));
            if (line.getUnit() != null && !line.getUnit().trim().isEmpty()) {
                stockText.append(' ').append(line.getUnit().trim());
            }
            binding.stock.setText(stockText.toString());

            // Show the price actually being charged, so it is visible and editable where
            // the cashier looks for it. Leaving this box blank hid the price entirely: the
            // row's only label was the hint, which disappears once the field has text, so
            // an empty box read as "fill this only to override" and prices were typed into
            // Cash received instead - which never reaches the sale.
            // An untouched row keeps override == null, so the drug default is still used.
            suppressWatcher = true;
            BigDecimal effective = line.effectivePrice();
            binding.price.setText(effective == null
                    ? "" : Money.toPlainString(effective));
            suppressWatcher = false;

            BigDecimal lineTotal = line.lineTotal();
            binding.lineTotal.setText(lineTotal == null
                    ? itemView.getContext().getString(R.string.pos_price_required_short)
                    : Money.format(lineTotal, currencySymbol));

            binding.decrease.setOnClickListener(v ->
                    listener.onQuantityChanged(line, line.getQuantity() - 1));
            binding.increase.setOnClickListener(v ->
                    listener.onQuantityChanged(line, line.getQuantity() + 1));
            binding.remove.setOnClickListener(v -> listener.onRemove(line));

            // The box is prefilled with the current price, so typing must replace it rather
            // than append: "0.75" + "1" would otherwise become "0.751".
            binding.price.setOnFocusChangeListener((v, focused) -> {
                if (focused && binding.price.getText() != null
                        && binding.price.getText().length() > 0) {
                    binding.price.selectAll();
                }
            });
            binding.price.setOnEditorActionListener((v, actionId, event) -> {
                // Already committed by the watcher; this only dismisses the keyboard.
                binding.price.clearFocus();
                return true;
            });

            String warning = warningFor(line);
            binding.warning.setVisibility(warning == null ? View.GONE : View.VISIBLE);
            binding.warning.setText(warning == null ? "" : warning);
        }

        /** Commits this row's price if it is the focused one. */
        void commitPriceIfFocused(@NonNull Listener listener) {
            CartLine line = boundLine;
            if (line != null && binding.price.hasFocus()) {
                listener.onPriceChanged(line, text(binding.price));
            }
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