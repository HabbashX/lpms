package com.lpms.ui.customers;

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
import com.lpms.data.dto.CustomerResponse;
import com.lpms.databinding.ItemCustomerBinding;

import java.util.Objects;

/**
 * Customer rows: name, contact details and state.
 *
 * <p>No debt column: {@link CustomerResponse} does not carry a balance, and inventing one
 * per row would cost a request per customer. The balance is on the statement screen.</p>
 */
public final class CustomersAdapter
        extends ListAdapter<CustomerResponse, CustomersAdapter.ViewHolder> {

    public interface Listener {
        void onCustomerClicked(@NonNull CustomerResponse customer);

        /** Sends the id; the Fragment resolves the full customer for the detail screen. */
        void onCustomerMenuClicked(@NonNull CustomerResponse customer, @NonNull View anchor);
    }

    private static final DiffUtil.ItemCallback<CustomerResponse> DIFF =
            new DiffUtil.ItemCallback<CustomerResponse>() {
                @Override
                public boolean areItemsTheSame(@NonNull CustomerResponse oldItem,
                                               @NonNull CustomerResponse newItem) {
                    return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull CustomerResponse oldItem,
                                                  @NonNull CustomerResponse newItem) {
                    return oldItem.isActive() == newItem.isActive()
                            && Objects.equals(oldItem.getName(), newItem.getName())
                            && Objects.equals(oldItem.getPhone(), newItem.getPhone())
                            && Objects.equals(oldItem.getAddress(), newItem.getAddress());
                }
            };

    private final Listener listener;

    public CustomersAdapter(@NonNull Listener listener) {
        super(DIFF);
        this.listener = listener;
        setHasStableIds(false);
    }

    @NonNull
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CustomerResponse customer = getItem(position);
        if (customer == null || customer.getId() == null) {
            holder.clear();
            return;
        }
        holder.bind(customer, listener);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_customer, parent, false));
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemCustomerBinding binding;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.binding = ItemCustomerBinding.bind(itemView);
        }

        void clear() {
            binding.name.setText("");
            binding.subtitle.setText("");
            binding.created.setText("");
            binding.badge.setVisibility(View.GONE);
        }

        void bind(@NonNull CustomerResponse customer, @NonNull Listener listener) {
            binding.name.setText(nullToEmpty(customer.getName()));

            StringBuilder subtitle = new StringBuilder();
            String phone = trimOrEmpty(customer.getPhone());
            String address = trimOrEmpty(customer.getAddress());
            if (!phone.isEmpty()) {
                subtitle.append(phone);
            }
            if (!address.isEmpty()) {
                if (subtitle.length() > 0) {
                    subtitle.append(" · ");
                }
                subtitle.append(address);
            }
            binding.subtitle.setText(subtitle.toString());
            binding.subtitle.setVisibility(subtitle.length() == 0 ? View.GONE : View.VISIBLE);

            binding.created.setText(Dates.date(binding.getRoot().getContext(),
                    customer.getCreatedAt() == null ? null
                            : java.time.LocalDate.ofInstant(customer.getCreatedAt(),
                            java.time.ZoneOffset.UTC)));

            boolean active = !customer.isActive();
            binding.badge.setVisibility(active ? View.GONE : View.VISIBLE);

            itemView.setOnClickListener(v -> listener.onCustomerClicked(customer));
        }

        @NonNull
        private static String trimOrEmpty(@Nullable String value) {
            if (value == null) {
                return "";
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? "" : trimmed;
        }

        @NonNull
        private static String nullToEmpty(@Nullable String value) {
            return value == null ? "" : value;
        }
    }
}