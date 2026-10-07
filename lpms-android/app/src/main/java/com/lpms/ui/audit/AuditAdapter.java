package com.lpms.ui.audit;

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
import com.lpms.data.dto.AuditLogResponse;
import com.lpms.databinding.ItemAuditBinding;

import java.util.Objects;

/**
 * Audit rows: the action, what it did, who did it and from where.
 *
 * <p>Read-only and never truncated: an entry that hides part of what happened is worse than
 * no entry, so the description is allowed two lines and the rest is visible by scrolling.</p>
 */
public final class AuditAdapter extends ListAdapter<AuditLogResponse, AuditAdapter.ViewHolder> {

    private static final DiffUtil.ItemCallback<AuditLogResponse> DIFF =
            new DiffUtil.ItemCallback<AuditLogResponse>() {
                @Override
                public boolean areItemsTheSame(@NonNull AuditLogResponse oldItem,
                                               @NonNull AuditLogResponse newItem) {
                    return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull AuditLogResponse oldItem,
                                                  @NonNull AuditLogResponse newItem) {
                    // Audit entries are immutable once written, so identity is enough.
                    return oldItem.getId().equals(newItem.getId());
                }
            };

    public AuditAdapter() {
        super(DIFF);
        setHasStableIds(false);
    }

    @NonNull
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AuditLogResponse entry = getItem(position);
        if (entry == null || entry.getId() == null) {
            holder.clear();
            return;
        }
        holder.bind(entry);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_audit, parent, false));
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemAuditBinding binding;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.binding = ItemAuditBinding.bind(itemView);
        }

        void clear() {
            binding.action.setText("");
            binding.when.setText("");
            binding.description.setText("");
            binding.who.setText("");
        }

        void bind(@NonNull AuditLogResponse entry) {
            binding.action.setText(nullToEmpty(entry.getAction()));
            binding.when.setText(Dates.time(binding.getRoot().getContext(), entry.getCreatedAt()));
            binding.description.setText(nullToEmpty(entry.getDescription()));
            binding.who.setText(attribution(entry));
        }

        /** Username, then IP and the affected record when the server supplied them. */
        @NonNull
        private String attribution(@NonNull AuditLogResponse entry) {
            StringBuilder who = new StringBuilder(nullToEmpty(entry.getUsername()));
            String ip = trimOrEmpty(entry.getIpAddress());
            if (!ip.isEmpty()) {
                if (who.length() > 0) {
                    who.append(" · ");
                }
                who.append(ip);
            }
            String entity = trimOrEmpty(entry.getEntityType());
            if (!entity.isEmpty() && entry.getEntityId() != null) {
                if (who.length() > 0) {
                    who.append(" · ");
                }
                who.append(entity).append('#').append(entry.getEntityId());
            }
            return who.toString();
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