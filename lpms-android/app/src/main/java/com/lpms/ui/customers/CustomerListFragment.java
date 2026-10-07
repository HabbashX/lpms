package com.lpms.ui.customers;

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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.lpms.R;
import com.lpms.core.error.ApiError;
import com.lpms.core.ui.ErrorPresenter;

import com.lpms.data.dto.CustomerResponse;
import com.lpms.data.repo.CustomerRepository;
import com.lpms.databinding.FragmentCustomerListBinding;
import com.lpms.databinding.ItemLoadStateBinding;
import com.lpms.ui.common.PagingUiState;

import dagger.hilt.android.AndroidEntryPoint;

/** Customer list: search, active filter, paging. */
@AndroidEntryPoint
public final class CustomerListFragment extends Fragment implements CustomersAdapter.Listener {

    /**
     * Navigation argument for the customer id, shared with the detail and form screens and
     * matching the {@code customerId} argument in the nav graph. 0 means "new customer".
     */
    public static final String ARG_CUSTOMER_ID = "customerId";

    private static final int PREFETCH_DISTANCE = 6;

    private FragmentCustomerListBinding binding;
    private CustomerListViewModel viewModel;
    private CustomersAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCustomerListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(CustomerListViewModel.class);

        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());

        setUpList();
        setUpSearch();
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
        adapter = new CustomersAdapter(this);
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
                binding.searchLayout.setError(null);
                binding.searchLayout.setErrorEnabled(false);
                viewModel.onSearchChanged(s == null ? "" : s.toString());
            }
        });
        binding.searchLayout.setEndIconMode(
                com.google.android.material.textfield.TextInputLayout.END_ICON_CLEAR_TEXT);
    }

    private void setUpChips() {
        binding.chipActive.setOnClickListener(v ->
                viewModel.setActive(isChecked(v) ? null : Boolean.TRUE));
        binding.chipClear.setOnClickListener(v -> {
            viewModel.clearFilters();
            binding.search.setText("");
            binding.chipActive.setChecked(false);
        });
    }

    private boolean isChecked(@NonNull View chip) {
        return ((com.google.android.material.chip.Chip) chip).isChecked();
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
                // Rows exist: keep them and report the failure without losing the list.
                Snackbar.make(binding.getRoot(), ErrorPresenter.message(requireContext(), error),
                        Snackbar.LENGTH_LONG).show();
            }
            return;
        }

        if (noRows) {
            if (state.isEmpty()) {
                CustomerRepository.Filter filter = viewModel.filter().getValue();
                showEmptyState(getString(filter != null && filter.hasAnyFilter()
                        ? R.string.customers_empty_filtered : R.string.customers_empty), false);
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

    private void updateFilterSummary(@Nullable CustomerRepository.Filter filter) {
        boolean activeOnly = filter != null && Boolean.TRUE.equals(filter.getActive());
        binding.chipActive.setChecked(activeOnly);
        binding.chipActive.setText(activeOnly
                ? getString(R.string.customers_filter_active_only)
                : getString(R.string.customers_filter_active));
        binding.chipClear.setVisibility(filter != null && filter.hasAnyFilter()
                ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onCustomerClicked(@NonNull CustomerResponse customer) {
        if (customer.getId() == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putLong(ARG_CUSTOMER_ID, customer.getId());
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_customerList_to_customerDetail, args);
    }

    @Override
    public void onCustomerMenuClicked(@NonNull CustomerResponse customer, @NonNull View anchor) {
        boolean active = !customer.isActive();
        String[] options = {
                getString(R.string.action_edit),
                getString(active ? R.string.customer_deactivate : R.string.customer_reactivate)
        };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(customer.getName() == null ? "" : customer.getName())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        openForm(customer);
                    } else {
                        viewModel.setCustomerActive(customer, !active);
                    }
                })
                .show();
    }

    private void openForm(@Nullable CustomerResponse customer) {
        Bundle args = new Bundle();
        if (customer != null && customer.getId() != null) {
            args.putLong(ARG_CUSTOMER_ID, customer.getId());
        }
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_customerList_to_customerForm, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
