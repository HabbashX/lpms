package com.lpms.ui.drugs;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.lpms.R;
import com.lpms.core.util.Money;
import com.lpms.data.dto.DrugResponse;
import com.lpms.databinding.ItemDrugBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Small draggable-free picker list reused by screens that only need "pick a drug"
 * (receive stock, later customer/purchase pickers).
 *
 * <p>Deliberately separate from {@code DrugsAdapter}: that one is a
 * {@code ListAdapter} for a paging list with row menus, this one is a plain adapter
 * bound to a plain {@code RecyclerView} with a tap callback.</p>
 */
public final class DrugsSearchRows {

    /** Row tap. */
    public interface Listener {
        void onPicked(@NonNull DrugResponse drug);
    }

    private final RecyclerView recyclerView;
    private final Listener listener;
    private final Adapter adapter = new Adapter();

    public DrugsSearchRows(@NonNull android.content.Context context,
                           @NonNull RecyclerView recyclerView,
                           @NonNull Listener listener) {
        this.recyclerView = recyclerView;
        this.listener = listener;
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(adapter);
    }

    public void submit(@Nullable List<DrugResponse> drugs) {
        adapter.items.clear();
        if (drugs != null) {
            adapter.items.addAll(drugs);
        }
        adapter.notifyDataSetChanged();
    }

    private final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {

        private final List<DrugResponse> items = new ArrayList<>();

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemDrugBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(items.get(position), listener);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class Holder extends RecyclerView.ViewHolder {

            private final ItemDrugBinding binding;

            Holder(@NonNull ItemDrugBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }

            void bind(@NonNull DrugResponse drug, @NonNull Listener onPicked) {
                binding.name.setText(drug.displayName());
                binding.subtitle.setText(drug.getGenericName() == null
                        ? "" : drug.getGenericName());
                binding.stock.setText(itemView.getContext().getString(
                        R.string.pos_available, drug.getCurrentQuantity()));
                binding.price.setText(drug.hasSellingPrice()
                        ? Money.format(drug.getSellingPrice(), symbol())
                        : itemView.getContext().getString(R.string.drug_price_not_set));
                binding.badge.setVisibility(drug.isActive() ? View.GONE : View.VISIBLE);
                binding.badge.setText(R.string.drug_inactive);
                binding.badge.setBackgroundResource(R.drawable.bg_badge_inactive);
                binding.menu.setVisibility(View.GONE);
                binding.getRoot().setOnClickListener(v -> onPicked.onPicked(drug));
            }
        }
    }

    /** Reads the app's currency setting without needing a full preferences wrapper. */
    private String symbol() {
        return new com.lpms.core.util.AppPreferences(recyclerView.getContext()).currencySymbol();
    }
}