package com.lpms.ui.drugs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.lpms.R;
import com.lpms.core.ui.ErrorPresenter;
import com.lpms.core.ui.StateRenderer;
import com.lpms.core.ui.UiState;
import com.lpms.core.util.AppPreferences;
import com.lpms.core.util.Dates;
import com.lpms.core.util.Money;
import com.lpms.data.dto.DrugResponse;
import com.lpms.databinding.FragmentDrugDetailBinding;
import com.lpms.databinding.ItemDetailRowBinding;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Drug detail.
 *
 * <p>Shows only what {@code DrugResponse} carries — nothing here needs cost data, so
 * there is no profit leak for EMPLOYEE. Edit / deactivate controls appear only for
 * ADMIN/PHARMACIST.</p>
 */
@AndroidEntryPoint
public final class DrugDetailFragment extends Fragment {

    private FragmentDrugDetailBinding binding;
    private DrugDetailViewModel viewModel;
    private StateRenderer stateRenderer;
    private DrugResponse current;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDrugDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(DrugDetailViewModel.class);

        binding.toolbar.setTitle(R.string.nav_drugs);
        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());

        stateRenderer = new StateRenderer(binding.state);
        stateRenderer.setRetryListener(viewModel::load);

        boolean canManage = viewModel.canManageCatalog();
        binding.editButton.setVisibility(canManage ? View.VISIBLE : View.GONE);
        binding.toggleStatusButton.setVisibility(canManage ? View.VISIBLE : View.GONE);
        binding.pricingButton.setVisibility(canManage ? View.VISIBLE : View.GONE);
        binding.editButton.setOnClickListener(v -> openForm());
        binding.pricingButton.setOnClickListener(v -> openPricing());
        binding.receiveStockButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_drugDetail_to_receiveStock));

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.busy().observe(getViewLifecycleOwner(), busy ->
                binding.toggleStatusButton.setEnabled(!Boolean.TRUE.equals(busy)));
        viewModel.message().observe(getViewLifecycleOwner(), message -> {
            if (message == null) {
                return;
            }
            Snackbar.make(binding.getRoot(), message.resolve(requireContext()),
                    Snackbar.LENGTH_LONG).show();
        });
        viewModel.closed().observe(getViewLifecycleOwner(), closed -> {
            if (Boolean.TRUE.equals(closed)) {
                NavHostFragment.findNavController(this).navigateUp();
            }
        });

        viewModel.load();
    }

    private void render(@Nullable UiState<DrugResponse> state) {
        stateRenderer.render(state, binding.content);
        if (state == null || !state.isContent()) {
            return;
        }
        DrugResponse drug = state.valueOrNull();
        if (drug == null) {
            return;
        }
        current = drug;
        bind(drug);
    }

    private void bind(@NonNull DrugResponse drug) {
        String currency = new AppPreferences(requireContext()).currencySymbol();
        binding.name.setText(drug.displayName());

        binding.statusBadge.setVisibility(drug.isActive() ? View.GONE : View.VISIBLE);
        binding.statusBadge.setText(R.string.drug_inactive);
        binding.statusBadge.setBackgroundResource(R.drawable.bg_badge_inactive);

        binding.rows.removeAllViews();
        addRow(getString(R.string.drug_generic_name), drug.getGenericName());
        addRow(getString(R.string.drug_barcode), drug.getBarcode());
        addRow(getString(R.string.drug_category), drug.getCategory());
        addRow(getString(R.string.drug_manufacturer), drug.getManufacturer());
        addRow(getString(R.string.drug_dosage_form), drug.getDosageForm());
        addRow(getString(R.string.drug_strength), drug.getStrength());
        addRow(getString(R.string.drug_unit), drug.getUnit());
        addRow(getString(R.string.drug_selling_price), drug.hasSellingPrice()
                ? Money.format(drug.getSellingPrice(), currency)
                : getString(R.string.drug_price_not_set));
        addRow(getString(R.string.drug_stock), getString(
                R.string.drug_stock_format, drug.getCurrentQuantity(), drug.getMinimumStockLevel()));
        addRow(getString(R.string.drug_description), drug.getDescription());
        addRow(getString(R.string.drug_updated_at),
                Dates.dateTime(requireContext(), drug.getUpdatedAt()));

        binding.toggleStatusButton.setText(
                drug.isActive() ? R.string.drug_deactivate : R.string.drug_reactivate);
        binding.toggleStatusButton.setOnClickListener(v -> confirmToggle(drug));
    }

    private void addRow(@NonNull String label, @Nullable String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        ItemDetailRowBinding row = ItemDetailRowBinding.inflate(
                LayoutInflater.from(requireContext()), binding.rows, false);
        row.label.setText(label);
        row.value.setText(value.trim());
        binding.rows.addView(row.getRoot());
    }

    private void confirmToggle(@NonNull DrugResponse drug) {
        boolean reactivate = !drug.isActive();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(reactivate ? R.string.drug_reactivate : R.string.drug_deactivate)
                .setMessage(reactivate ? R.string.drug_reactivate_confirm
                        : R.string.drug_deactivate_confirm)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_confirm, (dialog, which) ->
                        viewModel.setActive(drug, reactivate))
                .show();
    }

    /** Recommended pricing; only reachable for ADMIN/PHARMACIST. */
    private void openPricing() {
        if (current == null || current.getId() == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putLong(DrugListFragment.ARG_DRUG_ID, current.getId());
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_drugDetail_to_pricing, args);
    }

    private void openForm() {
        if (current == null || current.getId() == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putLong(DrugListFragment.ARG_DRUG_ID, current.getId());
        NavHostFragment.findNavController(this).navigate(R.id.action_drugDetail_to_drugForm, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        current = null;
    }
}