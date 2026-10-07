package com.lpms.ui.sales;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.lpms.R;
import com.lpms.core.util.Dates;
import com.lpms.core.util.Money;
import com.lpms.data.dto.SaleItemResponse;
import com.lpms.data.dto.SaleResponse;
import com.lpms.data.dto.SaleStatus;
import com.lpms.databinding.ItemSaleBinding;

import java.util.List;
import java.util.Objects;

/**
 * Sales history rows.
 *
 * <p>Cost and profit come back for every role, so they are hidden here for EMPLOYEE
 * rather than assumed absent. Nothing else in {@link SaleResponse} is role-gated: a
 * cashier may see every sale in the shop.</p>
 */
public final class SalesAdapter extends ListAdapter<SaleResponse, SalesAdapter.ViewHolder> {

    /** Row tap; the Fragment opens the receipt. */
    public interface Listener {
        void onSaleClicked(@NonNull SaleResponse sale);
    }

    private static final DiffUtil.ItemCallback<SaleResponse> DIFF =
            new DiffUtil.ItemCallback<SaleResponse>() {
                @Override
                public boolean areItemsTheSame(@NonNull SaleResponse oldItem,
                                               @NonNull SaleResponse newItem) {
                    return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull SaleResponse oldItem,
                                                  @NonNull SaleResponse newItem) {
                    // status and refundedTotal change when a refund lands, and the row shows
                    // both, so they must take part in the comparison.
                    return Objects.equals(oldItem.getStatus(), newItem.getStatus())
                            && Objects.equals(oldItem.getTotal(), newItem.getTotal())
                            && Objects.equals(oldItem.getAmountDue(), newItem.getAmountDue())
                            && Objects.equals(oldItem.getRefundedTotal(),
                            newItem.getRefundedTotal())
                            && Objects.equals(oldItem.getCreatedAt(), newItem.getCreatedAt());
                }
            };

    private final Listener listener;
    private final String currencySymbol;
    private final boolean canSeeProfit;

    public SalesAdapter(@NonNull Listener listener,
                        @NonNull String currencySymbol,
                        boolean canSeeProfit) {
        super(DIFF);
        this.listener = listener;
        this.currencySymbol = currencySymbol;
        this.canSeeProfit = canSeeProfit;
        setHasStableIds(false);
    }

    @NonNull
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SaleResponse sale = getItem(position);
        if (sale == null || sale.getId() == null) {
            // Paging runs without placeholders, so this only happens during a generation
            // swap; clearing stops the previous sale's figures being shown on this row.
            holder.clear();
            return;
        }
        holder.bind(sale, listener, currencySymbol, canSeeProfit);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sale, parent, false));
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemSaleBinding binding;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.binding = ItemSaleBinding.bind(itemView);
        }

        void clear() {
            binding.title.setText("");
            binding.subtitle.setText("");
            binding.detail.setText("");
            binding.total.setText("");
            binding.status.setText("");
            binding.status.setBackgroundResource(R.drawable.bg_badge_inactive);
            binding.due.setText("");
            binding.due.setVisibility(View.GONE);
        }

        void bind(@NonNull SaleResponse sale,
                  @NonNull Listener listener,
                  @NonNull String currency,
                  boolean canSeeProfit) {
            String number = sale.getId() == null ? "" : String.valueOf(sale.getId());
            binding.title.setText(binding.getRoot().getContext()
                    .getString(R.string.sales_row_title, number,
                            Dates.dateTime(binding.getRoot().getContext(), sale.getCreatedAt())));

            binding.subtitle.setText(subtitle(sale, currency, canSeeProfit));
            bindItems(sale);
            binding.total.setText(Money.format(sale.getTotal(), currency));
            bindStatus(sale);
            bindDue(sale, currency);

            itemView.setOnClickListener(v -> listener.onSaleClicked(sale));
        }

        private void bindItems(@NonNull SaleResponse sale) {
            List<SaleItemResponse> items = sale.getItems();
            if (items == null || items.isEmpty()) {
                binding.detail.setVisibility(View.GONE);
                return;
            }
            int units = 0;
            for (SaleItemResponse item : items) {
                units += item.getQuantity();
            }
            binding.detail.setText(binding.getRoot().getContext()
                    .getString(R.string.sales_row_lines, items.size(), units));
            binding.detail.setVisibility(View.VISIBLE);
        }

        private void bindStatus(@NonNull SaleResponse sale) {
            SaleStatus status = SaleStatus.fromNullable(sale.getStatus());
            String label;
            int background;
            if (status == SaleStatus.COMPLETED) {
                label = binding.getRoot().getContext().getString(R.string.sale_status_completed);
                background = R.drawable.bg_badge_ok;
            } else if (status == SaleStatus.PARTIALLY_REFUNDED) {
                label = binding.getRoot().getContext()
                        .getString(R.string.sale_status_partially_refunded);
                background = R.drawable.bg_badge_low;
            } else {
                // REFUNDED, or a status this build does not know: neutral rather than green.
                label = status == SaleStatus.REFUNDED
                        ? binding.getRoot().getContext().getString(R.string.sale_status_refunded)
                        : nullToEmpty(sale.getStatus());
                background = R.drawable.bg_badge_inactive;
            }
            binding.status.setText(label);
            binding.status.setBackgroundResource(background);
        }

        /** Only a sale on credit can carry a due amount; the rest must be paid in full. */
        private void bindDue(@NonNull SaleResponse sale, @NonNull String currency) {
            boolean hasDue = sale.getAmountDue() != null && sale.getAmountDue().signum() > 0;
            if (!hasDue) {
                binding.due.setVisibility(View.GONE);
                return;
            }
            binding.due.setText(binding.getRoot().getContext()
                    .getString(R.string.sales_row_due, Money.format(sale.getAmountDue(), currency)));
            binding.due.setVisibility(View.VISIBLE);
        }

        @NonNull
        private String subtitle(@NonNull SaleResponse sale,
                                @NonNull String currency,
                                boolean canSeeProfit) {
            String who = sale.getCustomerName();
            if (who == null || who.trim().isEmpty()) {
                who = binding.getRoot().getContext().getString(R.string.sale_walk_in);
            }
            String payment = sale.getPaymentMethod();
            String method = payment == null || payment.isEmpty()
                    ? binding.getRoot().getContext().getString(R.string.sale_unknown_method)
                    : payment;
            StringBuilder text = new StringBuilder(who).append(" · ").append(method);
            if (canSeeProfit && sale.getProfit() != null) {
                text.append(" · ").append(binding.getRoot().getContext()
                        .getString(R.string.sales_row_profit,
                                Money.format(sale.getProfit(), currency)));
            }
            return text.toString();
        }

        @NonNull
        private static String nullToEmpty(@androidx.annotation.Nullable String value) {
            return value == null ? "" : value;
        }
    }
}