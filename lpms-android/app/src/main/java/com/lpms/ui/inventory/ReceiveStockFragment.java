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
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.lpms.R;
import com.lpms.core.util.AppPreferences;
import com.lpms.core.util.Money;
import com.lpms.data.dto.DrugResponse;
import com.lpms.data.dto.StockBatchResponse;
import com.lpms.databinding.FragmentReceiveStockBinding;
import com.lpms.ui.common.FormError;
import com.lpms.ui.drugs.DrugsSearchRows;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Receive stock: pick a drug, enter quantity and purchase price, optionally supplier /
 * batch / expiry, and choose how (if at all) this purchase changes the selling price.
 *
 * <p>The pricing choice is a toggle, so <b>at most one</b> of
 * {@code sellingPrice} / {@code profitPerUnit} can ever be sent — the two-options
 * conflict (400 {@code INVALID_PRICING}) is structurally impossible from this screen.</p>
 *
 * <p>Buying the same drug again at a different price adds a new immutable batch; the
 * preview shows the resulting weighted-average cost.</p>
 */
@AndroidEntryPoint
public final class ReceiveStockFragment extends Fragment {

    private FragmentReceiveStockBinding binding;
    private ReceiveStockViewModel viewModel;
    private DrugsSearchRows rows;
    private String currencySymbol = "$";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentReceiveStockBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ReceiveStockViewModel.class);
        currencySymbol = new AppPreferences(requireContext()).currencySymbol();

        binding.toolbar.setTitle(R.string.stock_receive_title);
        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());

        rows = new DrugsSearchRows(requireContext(), binding.drugResults,
                this::onDrugPicked);
        binding.drugResults.setLayoutManager(new LinearLayoutManager(requireContext()));

        binding.drugSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                viewModel.searchDrugs(s == null ? "" : s.toString());
            }
        });

        binding.pricingGroup.check(R.id.pricing_none);
        binding.pricingGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            ReceiveStockViewModel.PricingOption option;
            if (checkedId == R.id.pricing_fixed) {
                option = ReceiveStockViewModel.PricingOption.FIXED_SELLING_PRICE;
            } else if (checkedId == R.id.pricing_profit) {
                option = ReceiveStockViewModel.PricingOption.PROFIT_PER_UNIT;
            } else {
                option = ReceiveStockViewModel.PricingOption.NONE;
            }
            binding.fixedPriceLayout.setVisibility(checkedId == R.id.pricing_fixed
                    ? View.VISIBLE : View.GONE);
            binding.profitLayout.setVisibility(checkedId == R.id.pricing_profit
                    ? View.VISIBLE : View.GONE);
            viewModel.setPricingOption(option);
        });

        binding.profit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                viewModel.loadPreviewIfProfit();
            }
        });

        binding.saveButton.setOnClickListener(v -> submit());

        viewModel.drugResults().observe(getViewLifecycleOwner(), results -> {
            rows.submit(results);
            binding.drugResults.setVisibility(
                    results == null || results.isEmpty() ? View.GONE : View.VISIBLE);
        });
        viewModel.selectedDrug().observe(getViewLifecycleOwner(), this::bindSelectedDrug);
        viewModel.preview().observe(getViewLifecycleOwner(), this::bindPreview);
        viewModel.submitting().observe(getViewLifecycleOwner(), busy -> {
            binding.saveButton.setEnabled(!Boolean.TRUE.equals(busy));
            binding.saveButton.setText(Boolean.TRUE.equals(busy)
                    ? R.string.stock_saving : R.string.stock_save);
            binding.progress.setVisibility(Boolean.TRUE.equals(busy) ? View.VISIBLE : View.GONE);
        });
        viewModel.quantityError().observe(getViewLifecycleOwner(), issue ->
                applyError(binding.quantityLayout, issue));
        viewModel.priceError().observe(getViewLifecycleOwner(), issue ->
                applyError(binding.unitPriceLayout, issue));
        viewModel.expirationError().observe(getViewLifecycleOwner(), issue ->
                applyError(binding.expirationLayout, issue));
        viewModel.toast().observe(getViewLifecycleOwner(), issue -> {
            if (issue == null) {
                return;
            }
            if (!viewModel.canManage()) {
                Snackbar.make(binding.getRoot(), issue.resolve(requireContext()),
                        Snackbar.LENGTH_LONG).show();
                return;
            }
            binding.errorBanner.setText(issue.resolve(requireContext()));
            binding.errorBanner.setVisibility(View.VISIBLE);
        });
        viewModel.created().observe(getViewLifecycleOwner(), this::onBatchCreated);
    }

    private void onDrugPicked(@NonNull DrugResponse drug) {
        viewModel.selectDrug(drug);
        binding.drugSearch.setText("");
        binding.drugResults.setVisibility(View.GONE);
    }

    private void bindSelectedDrug(@Nullable DrugResponse drug) {
        if (drug == null) {
            binding.selectedDrug.setVisibility(View.GONE);
            return;
        }
        binding.selectedDrug.setVisibility(View.VISIBLE);
        binding.selectedDrug.setText(getString(R.string.stock_selected_drug,
                drug.displayName(), drug.getCurrentQuantity()));
    }

    private void bindPreview(@Nullable ReceiveStockViewModel.Preview preview) {
        if (preview == null) {
            binding.previewCard.setVisibility(View.GONE);
            return;
        }
        binding.previewCard.setVisibility(View.VISIBLE);
        binding.previewStock.setText(getString(R.string.pricing_in_stock,
                preview.totalQuantity));
        binding.previewAverage.setText(getString(R.string.pricing_average_cost,
                Money.format(preview.averageCost, currencySymbol)));
        if (preview.suggestedSellingPrice != null) {
            binding.previewSuggested.setVisibility(View.VISIBLE);
            binding.previewSuggested.setText(getString(R.string.stock_new_price,
                    Money.format(preview.suggestedSellingPrice, currencySymbol)));
        } else {
            binding.previewSuggested.setVisibility(View.GONE);
        }
    }

    private void submit() {
        viewModel.submit(
                text(binding.quantity),
                text(binding.unitPrice),
                text(binding.supplier),
                text(binding.batchNumber),
                text(binding.expiration),
                text(binding.fixedPrice),
                text(binding.profit));
    }

    private void onBatchCreated(@Nullable StockBatchResponse batch) {
        if (batch == null) {
            return;
        }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.stock_received_title)
                .setMessage(getString(R.string.stock_received_message,
                        Money.format(batch.getUnitPurchasePrice(), currencySymbol),
                        batch.getQuantityReceived()))
                .setPositiveButton(R.string.action_close, (dialog, which) ->
                        NavHostFragment.findNavController(this).navigateUp())
                .setOnCancelListener(dialog ->
                        NavHostFragment.findNavController(this).navigateUp())
                .show();
    }

    private void applyError(@NonNull com.google.android.material.textfield.TextInputLayout layout,
                            @Nullable FormError issue) {
        if (issue == null) {
            layout.setError(null);
            layout.setErrorEnabled(false);
            return;
        }
        layout.setError(issue.resolve(requireContext()));
        layout.setErrorEnabled(true);
    }

    @NonNull
    private String text(@NonNull TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}