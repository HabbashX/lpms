package com.lpms.ui.sales;

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

import com.lpms.core.util.AppPreferences;
import com.lpms.data.dto.PaymentMethod;
import com.lpms.data.dto.SaleResponse;
import com.lpms.data.dto.SaleStatus;
import com.lpms.data.repo.SalesRepository;
import com.lpms.databinding.FragmentSalesListBinding;
import com.lpms.ui.common.PagingUiState;

import java.time.LocalDate;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Sales history.
 *
 * <p>Filters are exactly what {@code GET /sales} accepts &mdash; payment method, status
 * and an inclusive date range. The backend has no free-text search on this endpoint, so
 * none is offered.</p>
 *
 * <p>This is also where the POS sends a cashier after an unconfirmed sale, so the screen
 * deliberately carries no cached data: the whole point is to answer "did my sale go
 * through?" against the server.</p>
 */
@AndroidEntryPoint
public final class SalesListFragment extends Fragment implements SalesAdapter.Listener {

    /** Set by the POS when it arrives here after an uncertain confirmation. */
    public static final String ARG_HIGHLIGHT_SALE_ID = "highlightSaleId";

    private static final int PREFETCH_DISTANCE = 6;

    private FragmentSalesListBinding binding;
    private SalesListViewModel viewModel;
    private SalesAdapter adapter;
    private String currencySymbol;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSalesListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SalesListViewModel.class);
        currencySymbol = new AppPreferences(requireContext()).currencySymbol();

        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());

        setUpList();
        setUpChips();

        binding.swipeRefresh.setOnRefreshListener(() -> viewModel.refresh());

        viewModel.items().observe(getViewLifecycleOwner(), rows -> {
            if (adapter == null || rows == null) {
                return;
            }
            adapter.submitList(rows);
            scrollToHighlight();
        });
        viewModel.pagingState().observe(getViewLifecycleOwner(), this::renderPaging);
        viewModel.refreshing().observe(getViewLifecycleOwner(), refreshing ->
                binding.swipeRefresh.setRefreshing(Boolean.TRUE.equals(refreshing)));
        viewModel.filter().observe(getViewLifecycleOwner(), filter -> updateFilterSummary(filter));
        viewModel.message().observe(getViewLifecycleOwner(), issue -> {
            if (issue == null) {
                return;
            }
            Snackbar.make(binding.getRoot(), issue.resolve(requireContext()),
                    Snackbar.LENGTH_LONG).show();
        });

        Bundle args = getArguments();
        if (args != null && args.containsKey(ARG_HIGHLIGHT_SALE_ID)) {
            viewModel.highlight(args.getLong(ARG_HIGHLIGHT_SALE_ID));
        }
        viewModel.loadFirstPage();
    }

    private void setUpList() {
        adapter = new SalesAdapter(this, currencySymbol, viewModel.canSeeProfit());
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);
        binding.list.addItemDecoration(
                new DividerItemDecoration(requireContext(), LinearLayoutManager.VERTICAL));
        // Refunds change a row in place; animating it makes the list flicker on refresh.
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
    }

    private void setUpChips() {
        binding.chipPayment.setOnClickListener(this::showPaymentMenu);
        binding.chipStatus.setOnClickListener(this::showStatusMenu);
        binding.chipRange.setOnClickListener(v -> showRangeDialog());
        binding.chipClear.setOnClickListener(v -> {
            viewModel.clearFilters();
            binding.rangeLabel.setVisibility(View.GONE);
        });
    }

    // ------------------------------------------------------------------ filters

    private void showPaymentMenu(@NonNull View anchor) {
        androidx.appcompat.widget.PopupMenu menu =
                new androidx.appcompat.widget.PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 0, 0, R.string.sales_filter_any_payment);
        PaymentMethod[] values = PaymentMethod.values();
        for (int i = 0; i < values.length; i++) {
            menu.getMenu().add(0, i + 1, i + 1, values[i].name());
        }
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 0) {
                viewModel.setPaymentMethod(null);
            } else {
                viewModel.setPaymentMethod(values[item.getItemId() - 1].wireValue());
            }
            return true;
        });
        menu.show();
    }

    private void showStatusMenu(@NonNull View anchor) {
        androidx.appcompat.widget.PopupMenu menu =
                new androidx.appcompat.widget.PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 0, 0, R.string.sales_filter_any_status);
        SaleStatus[] values = SaleStatus.values();
        for (int i = 0; i < values.length; i++) {
            menu.getMenu().add(0, i + 1, i + 1, values[i].name());
        }
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 0) {
                viewModel.setStatus(null);
            } else {
                viewModel.setStatus(values[item.getItemId() - 1].wireValue());
            }
            return true;
        });
        menu.show();
    }

    /** Two optional {@code yyyy-MM-dd} fields; the server rejects from after to. */
    private void showRangeDialog() {
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_date_range, null, false);
        EditText from = content.findViewById(R.id.from);
        EditText to = content.findViewById(R.id.to);

        SalesRepository.Filter current = viewModel.filter().getValue();
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

    private void updateFilterSummary(@Nullable SalesRepository.Filter filter) {
        binding.chipClear.setVisibility(filter != null && !filter.isEmpty()
                ? View.VISIBLE : View.GONE);

        String method = filter == null ? null : filter.getPaymentMethod();
        binding.chipPayment.setText(method == null
                ? getString(R.string.sales_filter_payment)
                : method);

        String status = filter == null ? null : filter.getStatus();
        binding.chipStatus.setText(status == null
                ? getString(R.string.sales_filter_status)
                : status);

        LocalDate from = filter == null ? null : filter.getFrom();
        LocalDate to = filter == null ? null : filter.getTo();
        if (from == null && to == null) {
            binding.rangeLabel.setVisibility(View.GONE);
            binding.chipRange.setChecked(false);
            return;
        }
        binding.chipRange.setChecked(true);
        binding.rangeLabel.setText(getString(R.string.sales_range_summary,
                from == null ? "" : from.toString(),
                to == null ? "" : to.toString()));
        binding.rangeLabel.setVisibility(View.VISIBLE);
    }

    // ----------------------------------------------------------------- rendering

    private void renderPaging(@Nullable PagingUiState state) {
        if (state == null) {
            return;
        }
        binding.progress.setVisibility(state.isInitialLoading() ? View.VISIBLE : View.GONE);

        binding.loadMoreState.removeAllViews();
        if (state.isAppending()) {
            com.lpms.databinding.ItemLoadStateBinding footer =
                    com.lpms.databinding.ItemLoadStateBinding.inflate(
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
                // Nothing worth keeping on screen, so replace the list with the error.
                showEmptyState(ErrorPresenter.message(requireContext(), error), true);
            } else {
                // Rows exist: keep them and report the failure without losing the list.
                Snackbar.make(binding.getRoot(), ErrorPresenter.message(requireContext(), error),
                        Snackbar.LENGTH_LONG).show();
            }
            return;
        }

        if (noRows) {
            if (state.isEmpty()) {
                SalesRepository.Filter filter = viewModel.filter().getValue();
                boolean filtered = filter != null && !filter.isEmpty();
                showEmptyState(getString(filtered
                        ? R.string.sales_empty_filtered : R.string.sales_empty), false);
            }
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

    /** Scrolls the sale the POS was unsure about into view, once, when rows first land. */
    private void scrollToHighlight() {
        Long highlight = viewModel.highlightSaleId().getValue();
        if (highlight == null || adapter == null || binding.list.getLayoutManager() == null) {
            return;
        }
        int index = -1;
        List<SaleResponse> rows = viewModel.items().getValue();
        if (rows != null) {
            for (int i = 0; i < rows.size(); i++) {
                if (highlight.equals(rows.get(i).getId())) {
                    index = i;
                    break;
                }
            }
        }
        if (index >= 0) {
            binding.list.scrollToPosition(index);
        }
        // Only once: re-running on every page would yank the list around.
        viewModel.highlight(null);
    }

    @Override
    public void onSaleClicked(@NonNull SaleResponse sale) {
        if (sale.getId() == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putLong(SaleDetailFragment.ARG_SALE_ID, sale.getId());
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_salesList_to_saleDetail, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
