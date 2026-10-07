package com.lpms.ui.drugs;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.lpms.R;
import com.lpms.core.error.ApiError;
import com.lpms.core.ui.ErrorPresenter;
import com.lpms.core.util.AppPreferences;
import com.lpms.data.dto.CategoryResponse;
import com.lpms.data.dto.DosageForm;
import com.lpms.data.dto.DrugResponse;
import com.lpms.ui.common.PagingUiState;
import com.lpms.data.repo.DrugRepository;
import com.lpms.databinding.FragmentDrugListBinding;
import com.lpms.databinding.ItemLoadStateBinding;

import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Drug catalogue: search (matches name + genericName), category / dosage-form / status /
 * low-stock filters, sort menu and paged rows.
 *
 * <p>Write actions (add, edit, deactivate/reactivate) are only wired for
 * ADMIN/PHARMACIST; for EMPLOYEE the FAB and menu entries stay hidden because the server
 * would reject them anyway.</p>
 */
@AndroidEntryPoint
public final class DrugListFragment extends Fragment implements DrugsAdapter.Listener {

    private FragmentDrugListBinding binding;
    private DrugListViewModel viewModel;
    private DrugsAdapter adapter;
    private String currencySymbol = AppPreferences.CURRENCY_DEFAULT;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDrugListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(DrugListViewModel.class);
        currencySymbol = new AppPreferences(requireContext()).currencySymbol();

        setUpToolbar();
        setUpList();
        setUpSearch();
        setUpFilterChips();
        binding.emptyStateAction.setOnClickListener(v -> viewModel.loadFirstPage());

        binding.swipeRefresh.setOnRefreshListener(viewModel::refresh);
        viewModel.loadFirstPage();

        viewModel.items().observe(getViewLifecycleOwner(), rows -> adapter.submitList(rows));
        viewModel.pagingState().observe(getViewLifecycleOwner(), this::renderPaging);
        viewModel.refreshing().observe(getViewLifecycleOwner(), refreshing ->
                binding.swipeRefresh.setRefreshing(Boolean.TRUE.equals(refreshing)));
        viewModel.categories().observe(getViewLifecycleOwner(), categories -> {
            renderCategoryChip(categories);
            updateFilterSummary();
        });
        viewModel.toast().observe(getViewLifecycleOwner(), message -> {
            if (message == null) {
                return;
            }
            com.google.android.material.snackbar.Snackbar
                    .make(binding.getRoot(), message.resolve(requireContext()),
                            com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                    .show();
        });

        viewModel.loadCategories();
    }

    private void setUpToolbar() {
        binding.toolbar.setTitle(R.string.nav_drugs);
        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());
        binding.toolbar.inflateMenu(R.menu.drugs_menu);
        binding.toolbar.setOnMenuItemClickListener(this::onMenuItem);

        binding.addButton.setVisibility(viewModel.canManageCatalog() ? View.VISIBLE : View.GONE);
        binding.addButton.setOnClickListener(v -> openForm(null));
    }

    private boolean onMenuItem(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_sort) {
            showSortMenu(binding.toolbar);
            return true;
        }
        if (id == R.id.action_categories) {
            NavHostFragment.findNavController(this).navigate(R.id.action_drugList_to_categories);
            return true;
        }
        return false;
    }

    private void setUpList() {
        adapter = new DrugsAdapter(this, currencySymbol);
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);
        binding.list.addItemDecoration(
                new DividerItemDecoration(requireContext(), LinearLayoutManager.VERTICAL));
        binding.list.setItemAnimator(null);

        // Prefetch the next page as the user nears the end of the loaded rows.
        binding.list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || adapter == null) {
                    return;
                }
                RecyclerView.LayoutManager manager = recyclerView.getLayoutManager();
                if (manager instanceof LinearLayoutManager) {
                    LinearLayoutManager linear = (LinearLayoutManager) manager;
                    int lastVisible = linear.findLastVisibleItemPosition();
                    if (lastVisible >= adapter.getItemCount() - PREFETCH_DISTANCE) {
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

    private void setUpFilterChips() {
        binding.chipCategory.setOnClickListener(this::showCategoryMenu);
        binding.chipDosageForm.setOnClickListener(this::showDosageFormMenu);
        binding.chipStatus.setOnClickListener(this::showStatusMenu);
        binding.chipLowStock.setOnClickListener(v ->
                viewModel.setLowStock(isChecked(v) ? null : true));
        binding.chipClear.setOnClickListener(v -> {
            viewModel.clearFilters();
            binding.chipLowStock.setChecked(false);
            updateFilterSummary();
        });
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

        boolean noRows = adapter.getItemCount() == 0;
        if (state.isError()) {
            ApiError error = state.getError();
            if (error == null) {
                return;
            }
            if (noRows) {
                // Nothing to keep on screen: replace it with a full error state.
                showEmptyState(ErrorPresenter.message(requireContext(), error), true);
            } else {
                // Rows exist: keep them and surface the failure in the snackbar.
                showBanner(ErrorPresenter.message(requireContext(), error));
            }
            return;
        }
        if (noRows) {
            if (state.isEmpty()) {
                DrugRepository.Filter current = viewModel.filter().getValue();
                boolean filtered = current != null && current.hasAnyFilter();
                showEmptyState(getString(filtered
                        ? R.string.drugs_empty_filtered : R.string.drugs_empty), false);
            }
            return;
        }
        binding.emptyState.setVisibility(View.GONE);
    }

    private void showBanner(@NonNull String message) {
        com.google.android.material.snackbar.Snackbar.make(binding.getRoot(), message,
                com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();
    }

    private void showEmptyState(@NonNull String message, boolean showRetry) {
        binding.emptyState.setVisibility(View.VISIBLE);
        binding.emptyStateTitle.setText(message);
        binding.emptyStateAction.setVisibility(showRetry ? View.VISIBLE : View.GONE);
    }

    private void renderCategoryChip(@Nullable List<CategoryResponse> categories) {
        DrugRepository.Filter current = viewModel.filter().getValue();
        Long selected = current == null ? null : current.getCategoryId();
        String label = getString(R.string.drugs_filter_category);
        if (selected != null && categories != null) {
            for (CategoryResponse category : categories) {
                if (selected.equals(category.getId())) {
                    label = category.getName() == null ? label : category.getName();
                    break;
                }
            }
        }
        binding.chipCategory.setText(label);
    }

    private void updateFilterSummary() {
        DrugRepository.Filter current = viewModel.filter().getValue();
        int count = current == null ? 0 : current.activeFilterCount();
        binding.chipClear.setVisibility(count > 0 ? View.VISIBLE : View.GONE);

        String form = current == null ? null : current.getDosageForm();
        binding.chipDosageForm.setText(form == null
                ? getString(R.string.drugs_filter_dosage_form)
                : DosageForm.fromNullable(form).name());

        Boolean active = current == null ? null : current.getActive();
        binding.chipStatus.setChecked(active != null);
        binding.chipStatus.setText(active == null
                ? getString(R.string.drugs_filter_status)
                : getString(active ? R.string.drugs_status_active : R.string.drugs_status_inactive));

        boolean lowStock = current != null && Boolean.TRUE.equals(current.getLowStock());
        binding.chipLowStock.setChecked(lowStock);

        renderCategoryChip(viewModel.categories().getValue());
    }

    private void showCategoryMenu(@NonNull View anchor) {
        final List<CategoryResponse> categories = viewModel.categories().getValue();
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 0, 0, R.string.drugs_filter_any_category);
        if (categories != null) {
            for (int i = 0; i < categories.size(); i++) {
                CategoryResponse category = categories.get(i);
                menu.getMenu().add(0, i + 1, i + 1,
                        category.getName() == null ? "" : category.getName());
            }
        }
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 0) {
                viewModel.setCategory(null);
            } else if (categories != null) {
                viewModel.setCategory(categories.get(item.getItemId() - 1).getId());
            }
            updateFilterSummary();
            return true;
        });
        menu.show();
    }

    private void showDosageFormMenu(@NonNull View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 0, 0, R.string.drugs_filter_any_form);
        DosageForm[] values = DosageForm.values();
        for (int i = 0; i < values.length; i++) {
            menu.getMenu().add(0, i + 1, i + 1, values[i].name());
        }
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 0) {
                viewModel.setDosageForm(null);
            } else {
                viewModel.setDosageForm(values[item.getItemId() - 1].wireValue());
            }
            updateFilterSummary();
            return true;
        });
        menu.show();
    }

    private void showStatusMenu(@NonNull View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 0, 0, R.string.drugs_filter_any_status);
        menu.getMenu().add(0, 1, 1, R.string.drugs_status_active);
        menu.getMenu().add(0, 2, 2, R.string.drugs_status_inactive);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    viewModel.setActive(true);
                    break;
                case 2:
                    viewModel.setActive(false);
                    break;
                default:
                    viewModel.setActive(null);
                    break;
            }
            updateFilterSummary();
            return true;
        });
        menu.show();
    }

    private void showSortMenu(@NonNull View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        // Only the properties the backend documents; anything else is silently ignored
        // there, so the client must not offer them.
        menu.getMenu().add(0, 0, 0, R.string.sort_name_asc);
        menu.getMenu().add(0, 1, 1, R.string.sort_name_desc);
        menu.getMenu().add(0, 2, 2, R.string.sort_price_asc);
        menu.getMenu().add(0, 3, 3, R.string.sort_price_desc);
        menu.getMenu().add(0, 4, 4, R.string.sort_quantity_asc);
        menu.getMenu().add(0, 5, 5, R.string.sort_quantity_desc);
        menu.setOnMenuItemClickListener(item -> {
            String sort;
            switch (item.getItemId()) {
                case 1:
                    sort = DrugRepository.Filter.SORT_NAME_DESC;
                    break;
                case 2:
                    sort = DrugRepository.Filter.SORT_PRICE_ASC;
                    break;
                case 3:
                    sort = DrugRepository.Filter.SORT_PRICE_DESC;
                    break;
                case 4:
                    sort = DrugRepository.Filter.SORT_QUANTITY_ASC;
                    break;
                case 5:
                    sort = DrugRepository.Filter.SORT_QUANTITY_DESC;
                    break;
                default:
                    sort = DrugRepository.Filter.SORT_NAME_ASC;
                    break;
            }
            viewModel.setSort(sort);
            return true;
        });
        menu.show();
    }

    private void openReceiveStock() {
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_drugList_to_receiveStock);
    }

    private void openPricing(@Nullable Long drugId) {
        if (drugId == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putLong(ARG_DRUG_ID, drugId);
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_drugList_to_pricing, args);
    }

    private void openForm(@Nullable Long drugId) {
        Bundle args = new Bundle();
        if (drugId != null) {
            args.putLong(ARG_DRUG_ID, drugId);
        }
        NavHostFragment.findNavController(this).navigate(R.id.action_drugList_to_drugForm, args);
    }

    // ------------------------------------------------------- DrugsAdapter.Listener

    @Override
    public void onDrugClicked(@NonNull DrugResponse drug) {
        Bundle args = new Bundle();
        args.putLong(ARG_DRUG_ID, drug.getId() == null ? 0L : drug.getId());
        NavHostFragment.findNavController(this).navigate(R.id.action_drugList_to_drugDetail, args);
    }

    @Override
    public void onDrugMenuClicked(@NonNull DrugResponse drug, @NonNull View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        if (viewModel.canManageCatalog()) {
            // Pricing is role-gated: the endpoint is ADMIN/PHARMACIST only.
            menu.getMenu().add(0, ID_PRICING, ID_PRICING, R.string.pricing_title);
            menu.getMenu().add(0, ID_RECEIVE, ID_RECEIVE, R.string.stock_receive_title);
            menu.getMenu().add(0, ID_EDIT, ID_EDIT, R.string.action_edit);
            if (drug.isActive()) {
                menu.getMenu().add(0, ID_DEACTIVATE, ID_DEACTIVATE, R.string.drug_deactivate);
            } else {
                menu.getMenu().add(0, ID_REACTIVATE, ID_REACTIVATE, R.string.drug_reactivate);
            }
        }
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case ID_PRICING:
                    openPricing(drug.getId());
                    return true;
                case ID_RECEIVE:
                    openReceiveStock();
                    return true;
                case ID_EDIT:
                    openForm(drug.getId());
                    return true;
                case ID_DEACTIVATE:
                case ID_REACTIVATE:
                    confirmStatusChange(drug, item.getItemId() == ID_REACTIVATE);
                    return true;
                default:
                    return false;
            }
        });
        menu.show();
    }

    private void confirmStatusChange(@NonNull DrugResponse drug, boolean reactivate) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(reactivate ? R.string.drug_reactivate : R.string.drug_deactivate)
                .setMessage(reactivate ? R.string.drug_reactivate_confirm
                        : R.string.drug_deactivate_confirm)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_confirm, (dialog, which) ->
                        viewModel.setDrugActive(drug, reactivate))
                .show();
    }

    private static boolean isChecked(@NonNull View view) {
        return view instanceof com.google.android.material.chip.Chip
                && ((com.google.android.material.chip.Chip) view).isChecked();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /** Navigation argument key shared by the detail and form destinations. */
    /**
     * Navigation argument for a drug id, shared with the screens that open a single drug.
     * Matches the {@code drugId} argument in the nav graph; 0 means none.
     */
    public static final String ARG_DRUG_ID = "drugId";

    /** Rows from the end that trigger the next page request. */
    private static final int PREFETCH_DISTANCE = 6;

    private static final int ID_RECEIVE = 5;
    private static final int ID_PRICING = 4;
    private static final int ID_EDIT = 1;
    private static final int ID_DEACTIVATE = 2;
    private static final int ID_REACTIVATE = 3;
}