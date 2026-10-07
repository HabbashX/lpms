package com.lpms.ui.audit;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.lpms.R;
import com.lpms.core.error.ApiError;
import com.lpms.core.ui.ErrorPresenter;
import com.lpms.data.dto.AuditAction;
import com.lpms.data.dto.AuditLogResponse;
import com.lpms.data.repo.AuditRepository;
import com.lpms.databinding.FragmentAuditListBinding;
import com.lpms.databinding.ItemLoadStateBinding;
import com.lpms.ui.common.PagingUiState;

import java.time.LocalDate;

import dagger.hilt.android.AndroidEntryPoint;

/** Audit log. Read-only. */
@AndroidEntryPoint
public final class AuditListFragment extends Fragment {

    private static final int PREFETCH_DISTANCE = 10;

    private FragmentAuditListBinding binding;
    private AuditListViewModel viewModel;
    private AuditAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAuditListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AuditListViewModel.class);

        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());

        adapter = new AuditAdapter();
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);
        binding.list.addItemDecoration(
                new DividerItemDecoration(requireContext(), LinearLayoutManager.VERTICAL));
        // Entries are immutable, so a change animation would only flicker.
        binding.list.setItemAnimator(null);

        binding.list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0) {
                    return;
                }
                RecyclerView.LayoutManager manager = recyclerView.getLayoutManager();
                if (manager instanceof LinearLayoutManager && adapter != null) {
                    LinearLayoutManager linear = (LinearLayoutManager) manager;
                    if (linear.findLastVisibleItemPosition()
                            >= adapter.getItemCount() - PREFETCH_DISTANCE) {
                        viewModel.loadNextPage();
                    }
                }
            }
        });

        binding.chipAction.setOnClickListener(this::showActionMenu);
        binding.chipRange.setOnClickListener(v -> showRangeDialog());
        binding.chipClear.setOnClickListener(v -> {
            viewModel.clearFilters();
            binding.rangeLabel.setVisibility(View.GONE);
        });
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

    private void showActionMenu(@NonNull View anchor) {
        androidx.appcompat.widget.PopupMenu menu =
                new androidx.appcompat.widget.PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 0, 0, R.string.audit_filter_any_action);
        AuditAction[] values = AuditAction.values();
        for (int i = 0; i < values.length; i++) {
            menu.getMenu().add(0, i + 1, i + 1, values[i].name());
        }
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 0) {
                viewModel.setAction(null);
            } else {
                viewModel.setAction(values[item.getItemId() - 1].name());
            }
            return true;
        });
        menu.show();
    }

    private void showRangeDialog() {
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_date_range, null, false);
        EditText from = content.findViewById(R.id.from);
        EditText to = content.findViewById(R.id.to);

        AuditRepository.Filter current = viewModel.filter().getValue();
        if (current != null) {
            if (current.getFrom() != null) {
                from.setText(current.getFrom().toString());
            }
            if (current.getTo() != null) {
                to.setText(current.getTo().toString());
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.sales_filter_range)
                .setView(content)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_apply, (dialog, which) ->
                        viewModel.setRange(parseDate(from.getText().toString()),
                                parseDate(to.getText().toString())))
                .show();
    }

    @Nullable
    private static LocalDate parseDate(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(trimmed);
        } catch (RuntimeException notADate) {
            return null;
        }
    }

    private void updateFilterSummary(@Nullable AuditRepository.Filter filter) {
        binding.chipClear.setVisibility(filter != null && filter.hasAnyFilter()
                ? View.VISIBLE : View.GONE);

        String action = filter == null ? null : filter.getAction();
        binding.chipAction.setText(action == null
                ? getString(R.string.audit_filter_action) : action);

        LocalDate from = filter == null ? null : filter.getFrom();
        LocalDate to = filter == null ? null : filter.getTo();
        if (from == null && to == null) {
            binding.chipRange.setChecked(false);
            binding.rangeLabel.setVisibility(View.GONE);
            return;
        }
        binding.chipRange.setChecked(true);
        binding.rangeLabel.setText(getString(R.string.sales_range_summary,
                from == null ? "" : from.toString(),
                to == null ? "" : to.toString()));
        binding.rangeLabel.setVisibility(View.VISIBLE);
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
            AuditRepository.Filter filter = viewModel.filter().getValue();
            showEmptyState(getString(filter != null && filter.hasAnyFilter()
                    ? R.string.audit_empty_filtered : R.string.audit_empty), false);
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}