package com.lpms.ui.inventory;

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
import com.lpms.core.util.AppPreferences;
import com.lpms.core.util.Money;
import com.lpms.databinding.FragmentInventoryBinding;
import com.lpms.databinding.ItemLoadStateBinding;
import com.lpms.ui.common.PagingUiState;
import com.lpms.ui.drugs.DrugListFragment;

import java.math.BigDecimal;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Inventory: batches, low stock, expiring batches and valuation.
 *
 * <p>A pharmacist works through these together while ordering, so they are chips on one
 * screen rather than separate destinations.</p>
 */
@AndroidEntryPoint
public final class InventoryFragment extends Fragment implements InventoryAdapter.Listener {

    private static final int PREFETCH_DISTANCE = 6;

    private FragmentInventoryBinding binding;
    private InventoryViewModel viewModel;
    private InventoryAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentInventoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(InventoryViewModel.class);

        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());
        binding.toolbar.setTitle(R.string.inventory_title);

        adapter = new InventoryAdapter(this,
                new AppPreferences(requireContext()).currencySymbol());
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);
        binding.list.addItemDecoration(
                new DividerItemDecoration(requireContext(), LinearLayoutManager.VERTICAL));
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

        setUpModeChips();
        setUpSearch();

        binding.swipeRefresh.setOnRefreshListener(() -> viewModel.refresh());

        viewModel.mode().observe(getViewLifecycleOwner(), mode -> {
            if (mode != null) {
                applyModeTitle(mode);
            }
        });
        viewModel.items().observe(getViewLifecycleOwner(), rows -> {
            if (adapter == null || rows == null) {
                return;
            }
            adapter.submitList(rows);
            updateSummary(rows);
        });
        viewModel.pagingState().observe(getViewLifecycleOwner(), this::renderPaging);
        viewModel.refreshing().observe(getViewLifecycleOwner(), refreshing ->
                binding.swipeRefresh.setRefreshing(Boolean.TRUE.equals(refreshing)));

        viewModel.loadFirstPage();
    }

    private void setUpModeChips() {
        binding.modeGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                // selectionRequired keeps one chip checked; nothing to do.
                return;
            }
            int id = checkedIds.get(0);
            if (id == R.id.chip_low_stock) {
                viewModel.selectMode(InventoryViewModel.Mode.LOW_STOCK);
            } else if (id == R.id.chip_expiring) {
                viewModel.selectMode(InventoryViewModel.Mode.EXPIRING);
            } else if (id == R.id.chip_valuation) {
                viewModel.selectMode(InventoryViewModel.Mode.VALUATION);
            } else {
                viewModel.selectMode(InventoryViewModel.Mode.BATCHES);
            }
        });
    }

    private void setUpSearch() {
        binding.search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                viewModel.onSearchChanged(s == null ? "" : s.toString());
            }
        });
        binding.searchLayout.setEndIconMode(
                com.google.android.material.textfield.TextInputLayout.END_ICON_CLEAR_TEXT);
    }

    private void applyModeTitle(@NonNull InventoryViewModel.Mode mode) {
        binding.toolbar.setTitle(mode.titleRes);
        // The valuation total belongs to that mode only.
        if (mode != InventoryViewModel.Mode.VALUATION) {
            binding.summary.setVisibility(View.GONE);
        }
    }

    /**
     * Sums the valuation rows already loaded. This is the running total of what is on
     * screen, not the pharmacy's whole stock value: the server returns one row per drug on
     * a paged endpoint and no grand-total field, so a total over every page would mean
     * loading all of them.
     */
    private void updateSummary(@NonNull List<InventoryRow> rows) {
        if (viewModel.mode().getValue() != InventoryViewModel.Mode.VALUATION) {
            binding.summary.setVisibility(View.GONE);
            return;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (InventoryRow row : rows) {
            if (row.valuation != null && row.valuation.getTotalInventoryCost() != null) {
                total = total.add(row.valuation.getTotalInventoryCost());
            }
        }
        binding.summary.setText(getString(R.string.inventory_total_value,
                Money.format(total, new AppPreferences(requireContext()).currencySymbol())));
        binding.summary.setVisibility(View.VISIBLE);
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
            InventoryViewModel.Mode mode = viewModel.mode().getValue();
            showEmptyState(getString(mode == null
                    ? R.string.inventory_empty_batches : mode.emptyRes), false);
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
    public void onDrugClicked(@NonNull InventoryRow row, long drugId) {
        Bundle args = new Bundle();
        args.putLong(DrugListFragment.ARG_DRUG_ID, drugId);
        NavHostFragment.findNavController(this).navigate(R.id.action_inventory_to_drugDetail, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}