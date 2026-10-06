package com.lpms.ui.pricing;

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
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.lpms.R;
import com.lpms.core.ui.StateRenderer;
import com.lpms.core.ui.UiState;
import com.lpms.core.util.AppPreferences;
import com.lpms.core.util.Dates;
import com.lpms.core.util.Money;
import com.lpms.data.dto.DrugPricingResponse;
import com.lpms.data.dto.PricingBatchResponse;
import com.lpms.data.repo.InventoryRepository;
import com.lpms.databinding.FragmentDrugPricingBinding;
import com.lpms.databinding.ItemBatchBinding;
import com.lpms.databinding.ItemDetailRowBinding;

import java.math.BigDecimal;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Recommended pricing for one drug.
 *
 * <p>Shows the quantity-weighted average cost, the per-batch breakdown, the current
 * selling price and its profit/margin/markup, then lets the user drive a what-if:
 * pick <b>profit per unit</b> or <b>margin %</b>, and the endpoint returns
 * {@code suggestedSellingPrice}, which can be applied straight onto the drug.</p>
 *
 * <p>Only one what-if parameter is ever sent — the toggle makes that structural.</p>
 */
@AndroidEntryPoint
public final class DrugPricingFragment extends Fragment {

    /** Navigation argument: drug id. */
    public static final String ARG_DRUG_ID = "drugId";

    private FragmentDrugPricingBinding binding;
    private DrugPricingViewModel viewModel;
    private StateRenderer stateRenderer;
    private String currencySymbol = "$";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDrugPricingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(DrugPricingViewModel.class);
        currencySymbol = new AppPreferences(requireContext()).currencySymbol();

        binding.toolbar.setTitle(R.string.pricing_title);
        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());

        stateRenderer = new StateRenderer(binding.state);
        stateRenderer.setRetryListener(viewModel::load);

        // Profit per unit is the default what-if: one absolute value, no percentage maths.
        binding.modeGroup.check(R.id.mode_profit);
        binding.inputLayout.setHint(R.string.pricing_input_profit);
        binding.inputLayout.setSuffixText(getString(R.string.pricing_suffix_money));

        binding.modeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            boolean margin = checkedId == R.id.mode_margin;
            binding.inputLayout.setHint(margin
                    ? R.string.pricing_input_margin : R.string.pricing_input_profit);
            binding.inputLayout.setSuffixText(getString(margin
                    ? R.string.pricing_suffix_percent : R.string.pricing_suffix_money));
            binding.input.setText("");
            // Recompute with whatever is already typed in the other mode, if any.
            viewModel.selectMode(
                    margin ? InventoryRepository.PricingMode.MARGIN_PERCENT
                            : InventoryRepository.PricingMode.PROFIT_PER_UNIT,
                    margin ? null : text(),
                    margin ? text() : null);
        });

        binding.calculateButton.setOnClickListener(v -> {
            boolean margin = binding.modeGroup.getCheckedButtonId() == R.id.mode_margin;
            viewModel.selectMode(
                    margin ? InventoryRepository.PricingMode.MARGIN_PERCENT
                            : InventoryRepository.PricingMode.PROFIT_PER_UNIT,
                    margin ? null : text(),
                    margin ? text() : null);
        });

        binding.applyButton.setOnClickListener(v -> viewModel.applySuggestedPrice());

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.applying().observe(getViewLifecycleOwner(), busy -> {
            binding.applyButton.setEnabled(!Boolean.TRUE.equals(busy));
            binding.applyButton.setText(Boolean.TRUE.equals(busy)
                    ? R.string.pricing_applying : R.string.pricing_apply);
        });
        viewModel.toast().observe(getViewLifecycleOwner(), issue -> {
            if (issue == null) {
                return;
            }
            Snackbar.make(binding.getRoot(), issue.resolve(requireContext()),
                    Snackbar.LENGTH_LONG).show();
        });
        viewModel.applied().observe(getViewLifecycleOwner(), done -> {
            if (Boolean.TRUE.equals(done)) {
                NavHostFragment.findNavController(this).navigateUp();
            }
        });

        viewModel.load();
    }

    private void render(@Nullable UiState<DrugPricingResponse> state) {
        stateRenderer.render(state, binding.content);
        if (state == null || !state.isContent()) {
            return;
        }
        DrugPricingResponse pricing = state.valueOrNull();
        if (pricing == null) {
            return;
        }
        bindSummary(pricing);
        bindSuggestion(pricing);
        bindBatches(pricing.getBatches());
    }

    private void bindSummary(@NonNull DrugPricingResponse pricing) {
        String currency = currencySymbol;
        binding.drugName.setText(pricing.getDrugName() == null ? "" : pricing.getDrugName());
        binding.noStockWarning.setVisibility(
                DrugPricingViewModel.hasNoStock(pricing) ? View.VISIBLE : View.GONE);

        binding.summaryRows.removeAllViews();
        addRow(getString(R.string.pricing_quantity),
                String.valueOf(pricing.getTotalQuantity()));
        addRow(getString(R.string.pricing_total_cost),
                Money.format(pricing.getTotalInventoryCost(), currency));
        // Quantity-weighted, not a plain mean: 300@300 + 300@230 → 265.
        addRow(getString(R.string.pricing_average_cost),
                Money.format(pricing.getWeightedAverageCost(), currency), true);
        addRow(getString(R.string.pricing_current_price),
                pricing.hasSellingPrice()
                        ? Money.format(pricing.getSellingPrice(), currency)
                        : getString(R.string.drug_price_not_set));

        // These four are null unless the drug has both a price and stock.
        addRow(getString(R.string.pricing_profit_per_unit),
                Money.format(pricing.getProfitPerUnit(), currency));
        addRow(getString(R.string.pricing_margin),
                Money.percentValue(pricing.getMarginPercent(), java.util.Locale.getDefault()));
        addRow(getString(R.string.pricing_markup),
                Money.percentValue(pricing.getMarkupPercent(), java.util.Locale.getDefault()));
        addRow(getString(R.string.pricing_expected_profit),
                Money.format(pricing.getExpectedTotalProfit(), currency));
    }

    private void bindSuggestion(@NonNull DrugPricingResponse pricing) {
        BigDecimal suggested = pricing.getSuggestedSellingPrice();
        boolean hasSuggestion = suggested != null;
        binding.suggestionCard.setVisibility(hasSuggestion ? View.VISIBLE : View.GONE);
        if (!hasSuggestion) {
            return;
        }
        String currency = currencySymbol;
        binding.suggestedPrice.setText(Money.format(suggested, currency));

        InventoryRepository.PricingMode current = viewModel.mode().getValue();
        BigDecimal cost = pricing.getWeightedAverageCost();
        if (current == InventoryRepository.PricingMode.MARGIN_PERCENT) {
            binding.suggestedDetail.setText(getString(R.string.pricing_detail_margin,
                    Money.format(cost, currency),
                    Money.percentValue(pricing.getMarginPercent(), java.util.Locale.getDefault())));
        } else if (current == InventoryRepository.PricingMode.PROFIT_PER_UNIT) {
            binding.suggestedDetail.setText(getString(R.string.pricing_detail_profit,
                    Money.format(cost, currency),
                    Money.format(pricing.getProfitPerUnit(), currency)));
        } else {
            binding.suggestedDetail.setText("");
        }
    }

    private void bindBatches(@NonNull List<PricingBatchResponse> batches) {
        binding.batchesList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.batchesList.setNestedScrollingEnabled(false);
        if (batches.isEmpty()) {
            binding.batchesList.setVisibility(View.GONE);
            return;
        }
        binding.batchesList.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        binding.batchesList.removeAllViews();
        for (PricingBatchResponse batch : batches) {
            ItemBatchBinding row = ItemBatchBinding.inflate(inflater, binding.batchesList, false);
            String label = batch.getBatchNumber() == null || batch.getBatchNumber().trim().isEmpty()
                    ? getString(R.string.pricing_batch_unnamed)
                    : batch.getBatchNumber().trim();
            row.batchLabel.setText(label);
            String expiry = batch.getExpirationDate() == null
                    ? getString(R.string.pricing_batch_no_expiry)
                    : Dates.date(requireContext(), batch.getExpirationDate());
            row.batchDetail.setText(getString(R.string.pricing_batch_detail,
                    batch.getRemainingQuantity(), expiry));
            BigDecimal cost = batch.getUnitPurchasePrice() == null ? null
                    : batch.getUnitPurchasePrice()
                    .multiply(BigDecimal.valueOf(batch.getRemainingQuantity()));
            row.batchCost.setText(Money.format(cost, currencySymbol));
            binding.batchesList.addView(row.getRoot());
        }
    }

    private void addRow(@NonNull String label, @NonNull String value) {
        addRow(label, value, false);
    }

    private void addRow(@NonNull String label, @NonNull String value, boolean emphasise) {
        ItemDetailRowBinding row = ItemDetailRowBinding.inflate(
                LayoutInflater.from(requireContext()), binding.summaryRows, false);
        row.label.setText(label);
        row.value.setText(value);
        if (emphasise) {
            row.value.setTypeface(row.value.getTypeface(), android.graphics.Typeface.BOLD);
        }
        binding.summaryRows.addView(row.getRoot());
    }

    @NonNull
    private String text() {
        return binding.input.getText() == null ? "" : binding.input.getText().toString().trim();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}