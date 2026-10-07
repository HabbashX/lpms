package com.lpms.ui.users;

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
import com.lpms.data.dto.UserResponse;
import com.lpms.databinding.ItemUserBinding;

import java.util.Objects;

/** Staff rows: username, role, and whether the account is enabled. */
public final class UsersAdapter extends ListAdapter<UserResponse, UsersAdapter.ViewHolder> {

    public interface Listener {
        void onUserClicked(@NonNull UserResponse user);

        void onUserMenuClicked(@NonNull UserResponse user, @NonNull View anchor);
    }

    private static final DiffUtil.ItemCallback<UserResponse> DIFF =
            new DiffUtil.ItemCallback<UserResponse>() {
                @Override
                public boolean areItemsTheSame(@NonNull UserResponse oldItem,
                                               @NonNull UserResponse newItem) {
                    return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull UserResponse oldItem,
                                                  @NonNull UserResponse newItem) {
                    return oldItem.isEnabled() == newItem.isEnabled()
                            && Objects.equals(oldItem.getUsername(), newItem.getUsername())
                            && Objects.equals(oldItem.getRole(), newItem.getRole())
                            && Objects.equals(oldItem.getLastLoginAt(),
                            newItem.getLastLoginAt());
                }
            };

    private final Listener listener;

    public UsersAdapter(@NonNull Listener listener) {
        super(DIFF);
        this.listener = listener;
        setHasStableIds(false);
    }

    @NonNull
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UserResponse user = getItem(position);
        if (user == null || user.getId() == null) {
            holder.clear();
            return;
        }
        holder.bind(user, listener);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user, parent, false));
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemUserBinding binding;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.binding = ItemUserBinding.bind(itemView);
        }

        void clear() {
            binding.username.setText("");
            binding.subtitle.setText("");
            binding.badge.setVisibility(View.GONE);
        }

        void bind(@NonNull UserResponse user, @NonNull Listener listener) {
            binding.username.setText(user.getUsername() == null ? "" : user.getUsername());
            binding.subtitle.setText(subtitle(user));
            // A disabled account is the only state worth a badge.
            binding.badge.setVisibility(user.isEnabled() ? View.GONE : View.VISIBLE);

            itemView.setOnClickListener(v -> listener.onUserClicked(user));
            binding.menu.setOnClickListener(v -> listener.onUserMenuClicked(user, binding.menu));
        }

        @NonNull
        private String subtitle(@NonNull UserResponse user) {
            StringBuilder text = new StringBuilder(
                    user.getRole() == null ? "" : user.getRole());
            // "never" is meaningful to an admin: the account has never signed in.
            String lastSeen = user.getLastLoginAt() == null
                    ? binding.getRoot().getContext().getString(R.string.user_never_signed_in)
                    : Dates.dateTime(binding.getRoot().getContext(), user.getLastLoginAt());
            text.append(" · ").append(binding.getRoot().getContext()
                    .getString(R.string.user_last_seen, lastSeen));
            if (user.isMustChangePassword()) {
                text.append(" · ").append(binding.getRoot().getContext()
                        .getString(R.string.user_must_change_password));
            }
            return text.toString();
        }

        @Nullable
        static String roleOrEmpty(@Nullable String role) {
            return role == null ? "" : role;
        }
    }
}