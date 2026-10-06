package com.lpms.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.lpms.R;
import com.lpms.core.util.Money;
import com.lpms.data.dto.DashboardResponse;
import com.lpms.databinding.ItemTopDrugBinding;

/**
 * "Top selling drugs" rows from {@code GET /dashboard}.
 *
 * <p>{@link ListAdapter} + {@link DiffUtil} so a refresh animates rows instead of
 * blinking. Rows show quantity and revenue only — this endpoint exposes no cost or
 * profit for these rows.</p>
 */
public final class TopDrugsAdapter
        extends ListAdapter<DashboardResponse.TopSellingDrug, TopDrugsAdapter.ViewHolder> {

    /** Currency symbol comes from local settings; the backend has no currency field. */
    private final String currencySymbol;

    public TopDrugsAdapter(@NonNull String currencySymbol) {
        super(DIFF);
        this.currencySymbol = currencySymbol;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_top_drug, parent, false);
        return new ViewHolder(view, currencySymbol);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), position + 1);
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemTopDrugBinding binding;
        private final String currencySymbol;

        ViewHolder(@NonNull View itemView, @NonNull String currencySymbol) {
            super(itemView);
            this.binding = ItemTopDrugBinding.bind(itemView);
            this.currencySymbol = currencySymbol;
        }

        void bind(@NonNull DashboardResponse.TopSellingDrug drug, int rank) {
            binding.rank.setText(String.valueOf(rank));
            binding.drugName.setText(drug.getDrugName() == null ? "" : drug.getDrugName());
            binding.quantity.setText(itemView.getContext()
                    .getString(R.string.dashboard_units_sold, drug.getQuantity()));
            binding.revenue.setText(Money.format(drug.getRevenue(), currencySymbol));
        }
    }

    private static final DiffUtil.ItemCallback<DashboardResponse.TopSellingDrug> DIFF =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(@NonNull DashboardResponse.TopSellingDrug oldItem,
                                               @NonNull DashboardResponse.TopSellingDrug newItem) {
                    return equal(oldItem.getDrugId(), newItem.getDrugId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull DashboardResponse.TopSellingDrug oldItem,
                                                  @NonNull DashboardResponse.TopSellingDrug newItem) {
                    return oldItem.getQuantity() == newItem.getQuantity()
                            && equal(oldItem.getRevenue(), newItem.getRevenue())
                            && equal(oldItem.getDrugName(), newItem.getDrugName());
                }

                private boolean equal(Object a, Object b) {
                    return a == null ? b == null : a.equals(b);
                }
            };
}