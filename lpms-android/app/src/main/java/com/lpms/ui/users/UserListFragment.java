package com.lpms.ui.users;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.lpms.R;
import com.lpms.core.error.ApiError;
import com.lpms.core.ui.ErrorPresenter;
import com.lpms.core.util.Dates;
import com.lpms.data.dto.UserResponse;
import com.lpms.data.repo.UserRepository;
import com.lpms.databinding.FragmentUserListBinding;
import com.lpms.databinding.ItemLoadStateBinding;
import com.lpms.ui.common.PagingUiState;

import dagger.hilt.android.AndroidEntryPoint;

/** Staff accounts: list, with role and status filters. ADMIN only. */
@AndroidEntryPoint
public final class UserListFragment extends Fragment implements UsersAdapter.Listener {

    /**
     * Navigation argument for a user id, shared with the form screen and matching the
     * {@code userId} argument in the nav graph. 0 means "new user".
     */
    public static final String ARG_USER_ID = "userId";

    private static final int PREFETCH_DISTANCE = 6;

    private FragmentUserListBinding binding;
    private UserListViewModel viewModel;
    private UsersAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentUserListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(UserListViewModel.class);

        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());

        setUpList();
        setUpChips();

        binding.addButton.setOnClickListener(v -> openForm(null));
        binding.swipeRefresh.setOnRefreshListener(() -> viewModel.refresh());

        viewModel.items().observe(getViewLifecycleOwner(), rows -> {
            if (adapter != null && rows != null) {
                adapter.submitList(rows);
            }
        });
        viewModel.pagingState().observe(getViewLifecycleOwner(), this::renderPaging);
        viewModel.refreshing().observe(getViewLifecycleOwner(), refreshing ->
                binding.swipeRefresh.setRefreshing(Boolean.TRUE.equals(refreshing)));
        viewModel.filter().observe(getViewLifecycleOwner(), this::updateFilterSummary);
        viewModel.message().observe(getViewLifecycleOwner(), issue -> {
            if (issue == null) {
                return;
            }
            Snackbar.make(binding.getRoot(), issue.resolve(requireContext()),
                    Snackbar.LENGTH_LONG).show();
        });

        viewModel.loadFirstPage();
    }

    private void setUpList() {
        adapter = new UsersAdapter(this);
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);
        binding.list.addItemDecoration(
                new DividerItemDecoration(requireContext(), LinearLayoutManager.VERTICAL));
        binding.list.setItemAnimator(null);

        binding.list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || adapter == null) {
                    return;
                }
                RecyclerView.LayoutManager manager = recyclerView.getLayoutManager();
                if (manager instanceof LinearLayoutManager) {
                    LinearLayoutManager linear = (LinearLayoutManager) manager;
                    if (linear.findLastVisibleItemPosition()
                            >= adapter.getItemCount() - PREFETCH_DISTANCE) {
                        viewModel.loadNextPage();
                    }
                }
            }
        });
    }

    private void setUpChips() {
        binding.chipRole.setOnClickListener(this::showRoleMenu);
        binding.chipEnabled.setOnClickListener(v -> {
            com.google.android.material.chip.Chip chip =
                    (com.google.android.material.chip.Chip) v;
            viewModel.setEnabled(chip.isChecked() ? null : Boolean.TRUE);
        });
        binding.chipClear.setOnClickListener(v -> {
            viewModel.clearFilters();
            binding.chipEnabled.setChecked(false);
        });
    }

    /** Only the three roles the backend defines; anything else is not assignable. */
    private void showRoleMenu(@NonNull View anchor) {
        androidx.appcompat.widget.PopupMenu menu =
                new androidx.appcompat.widget.PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 0, 0, R.string.users_filter_any_role);
        com.lpms.core.auth.Role[] roles = com.lpms.core.auth.Role.values();
        for (int i = 0; i < roles.length; i++) {
            menu.getMenu().add(0, i + 1, i + 1, roles[i].name());
        }
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 0) {
                viewModel.setRole(null);
            } else {
                viewModel.setRole(roles[item.getItemId() - 1].name());
            }
            return true;
        });
        menu.show();
    }

    private void renderPaging(@Nullable PagingUiState state) {
        if (state == null) {
            return;
        }
        binding.progress.setVisibility(state.isInitialLoading() ? View.VISIBLE : View.GONE);

        binding.loadMoreState.removeAllViews();
        if (state.isAppending()) {
            ItemLoadStateBinding footer = ItemLoadStateBinding.inflate(
                    LayoutInflater.from(requireContext()), binding.loadMoreState, false);
            binding.loadMoreState.addView(footer.getRoot());
        }

        boolean noRows = adapter == null || adapter.getItemCount() == 0;
        if (state.isError()) {
            ApiError error = state.getError();
            if (error == null) {
                return;
            }
            if (noRows) {
                showEmptyState(ErrorPresenter.message(requireContext(), error), true);
            } else {
                Snackbar.make(binding.getRoot(), ErrorPresenter.message(requireContext(), error),
                        Snackbar.LENGTH_LONG).show();
            }
            return;
        }

        if (noRows && state.isEmpty()) {
            UserRepository.Filter filter = viewModel.filter().getValue();
            showEmptyState(getString(filter != null && filter.hasAnyFilter()
                    ? R.string.users_empty_filtered : R.string.users_empty), false);
            return;
        }
        if (noRows) {
            binding.emptyState.setVisibility(View.GONE);
            return;
        }
        binding.emptyState.setVisibility(View.GONE);
    }

    private void showEmptyState(@NonNull String message, boolean showRetry) {
        binding.emptyState.setVisibility(View.VISIBLE);
        binding.emptyStateTitle.setText(message);
        binding.emptyStateAction.setVisibility(showRetry ? View.VISIBLE : View.GONE);
        binding.emptyStateAction.setOnClickListener(v -> viewModel.loadFirstPage());
    }

    private void updateFilterSummary(@Nullable UserRepository.Filter filter) {
        String role = filter == null ? null : filter.getRole();
        binding.chipRole.setText(role == null
                ? getString(R.string.users_filter_role) : role);

        boolean enabledOnly = filter != null && Boolean.TRUE.equals(filter.getEnabled());
        binding.chipEnabled.setChecked(enabledOnly);
        binding.chipClear.setVisibility(filter != null && filter.hasAnyFilter()
                ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onUserClicked(@NonNull UserResponse user) {
        openForm(user);
    }

    @Override
    public void onUserMenuClicked(@NonNull UserResponse user, @NonNull View anchor) {
        String[] options = {
                getString(R.string.action_edit),
                getString(R.string.user_reset_password),
                getString(user.isEnabled() ? R.string.user_disable : R.string.user_enable)
        };
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(user.getUsername() == null ? "" : user.getUsername())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        openForm(user);
                    } else if (which == 1) {
                        promptResetPassword(user);
                    } else {
                        viewModel.setEnabled(user, !user.isEnabled());
                    }
                })
                .show();
    }

    private void promptResetPassword(@NonNull UserResponse user) {
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_amount, null, false);
        android.widget.EditText amount = content.findViewById(R.id.amount);
        amount.setHint(getString(R.string.user_new_password));

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.user_reset_password)
                .setView(content)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, (dialog, which) ->
                        viewModel.resetPassword(user, amount.getText().toString()))
                .show();
    }

    private void openForm(@Nullable UserResponse user) {
        Bundle args = new Bundle();
        if (user != null && user.getId() != null) {
            args.putLong(ARG_USER_ID, user.getId());
        }
        NavHostFragment.findNavController(this).navigate(R.id.action_userList_to_userForm, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}