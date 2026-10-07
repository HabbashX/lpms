package com.lpms.ui.reports;

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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.lpms.R;
import com.lpms.core.ui.StateRenderer;
import com.lpms.core.ui.UiState;
import com.lpms.core.util.AppPreferences;
import com.lpms.core.util.Dates;
import com.lpms.core.util.Money;
import com.lpms.data.dto.ProfitDetailResponse;
import com.lpms.data.dto.ProfitPreset;
import com.lpms.data.dto.ProfitReportResponse;
import com.lpms.databinding.FragmentReportsBinding;
import com.lpms.databinding.ItemDetailRowBinding;
import com.lpms.databinding.ItemProfitDetailBinding;
import com.lpms.ui.common.FormError;

import java.time.LocalDate;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Profit reports.
 *
 * <p>Admin and pharmacist only; the More hub hides the row for an employee, and the backend
 * rejects the request regardless, so profit never reaches a cashier's screen.</p>
 */
@AndroidEntryPoint
public final class ReportsFragment extends Fragment {

    private FragmentReportsBinding binding;
    private ReportsViewModel viewModel;
    private StateRenderer stateRenderer;
    private String currencySymbol;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentReportsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ReportsViewModel.class);
        currencySymbol = new AppPreferences(requireContext()).currencySymbol();

        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());

        stateRenderer = new StateRenderer(binding.state);
        stateRenderer.setRetryListener(viewModel::load);

        binding.swipeRefresh.setOnRefreshListener(() -> viewModel.refresh());
        binding.moreBreakdown.setOnClickListener(v -> viewModel.loadMoreBreakdown());

        setUpPresetChips();

        viewModel.summary().observe(getViewLifecycleOwner(), this::renderSummary);
        viewModel.breakdown().observe(getViewLifecycleOwner(), this::renderBreakdown);
        viewModel.loadingMoreBreakdown().observe(getViewLifecycleOwner(), loading ->
                binding.moreBreakdown.setEnabled(!Boolean.TRUE.equals(loading)));
        viewModel.message().observe(getViewLifecycleOwner(), issue -> {
            if (issue == null) {
                return;
            }
            Snackbar.make(binding.getRoot(), issue.resolve(requireContext()),
                    Snackbar.LENGTH_LONG).show();
        });

        viewModel.load();
    }

    private void setUpPresetChips() {
        binding.presetGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            int id = checkedIds.get(0);
            if (id == R.id.chip_yesterday) {
                viewModel.selectPreset(ProfitPreset.YESTERDAY);
            } else if (id == R.id.chip_this_week) {
                viewModel.selectPreset(ProfitPreset.THIS_WEEK);
            } else if (id == R.id.chip_last_week) {
                viewModel.selectPreset(ProfitPreset.LAST_WEEK);
            } else if (id == R.id.chip_this_month) {
                viewModel.selectPreset(ProfitPreset.THIS_MONTH);
            } else if (id == R.id.chip_last_month) {
                viewModel.selectPreset(ProfitPreset.LAST_MONTH);
            } else if (id == R.id.chip_custom) {
                showRangeDialog();
            } else {
                viewModel.selectPreset(ProfitPreset.TODAY);
            }
        });
    }

    private void showRangeDialog() {
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_date_range, null, false);
        EditText from = content.findViewById(R.id.from);
        EditText to = content.findViewById(R.id.to);

        ProfitPreset.DateRange current = viewModel.currentRange();
        from.setText(current.getFrom().toString());
        to.setText(current.getTo().toString());

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.reports_custom_range)
                .setView(content)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_apply, (dialog, which) ->
                        viewModel.selectCustomRange(parseDate(from.getText().toString()),
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

    private void renderSummary(@Nullable UiState<ProfitReportResponse> state) {
        stateRenderer.render(state, binding.content);
        if (state == null || !state.isContent()) {
            return;
        }
        ProfitReportResponse report = state.valueOrNull();
        if (report == null) {
            return;
        }

        ProfitPreset.DateRange range = viewModel.currentRange();
        binding.period.setText(getString(R.string.reports_period,
                Dates.shortDate(requireContext(), range.getFrom()),
                Dates.shortDate(requireContext(), range.getTo())));

        binding.summaryRows.removeAllViews();
        addRow(getString(R.string.reports_sales_count), String.valueOf(report.getSalesCount()));
        addRow(getString(R.string.reports_revenue),
                Money.format(report.getRevenue(), currencySymbol));
        addRow(getString(R.string.sale_cost), Money.format(report.getCost(), currencySymbol));
        // Profit is the figure the report exists for, so it is emphasised.
        addRow(getString(R.string.sale_profit),
                Money.format(report.getProfit(), currencySymbol), true);
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

    private void renderBreakdown(@Nullable List<ProfitDetailResponse> rows) {
        binding.breakdown.removeAllViews();
        boolean none = rows == null || rows.isEmpty();
        binding.breakdownHint.setVisibility(none ? View.VISIBLE : View.GONE);
        binding.moreBreakdown.setVisibility(
                !none && viewModel.hasMoreBreakdown() ? View.VISIBLE : View.GONE);
        if (none) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (ProfitDetailResponse row : rows) {
            ItemProfitDetailBinding line = ItemProfitDetailBinding.inflate(
                    inflater, binding.breakdown, false);
            line.title.setText(row.getDrug() == null ? "" : row.getDrug());
            line.subtitle.setText(breakdownSubtitle(row));
            line.profit.setText(Money.format(row.getProfit(), currencySymbol));
            line.revenue.setText(getString(R.string.reports_line_revenue,
                    Money.format(row.getRevenue(), currencySymbol)));
            binding.breakdown.addView(line.getRoot());
        }
    }

    @NonNull
    private String breakdownSubtitle(@NonNull ProfitDetailResponse row) {
        StringBuilder meta = new StringBuilder();
        if (row.getSaleId() != null) {
            meta.append('#').append(row.getSaleId());
        }
        if (row.getDate() != null) {
            if (meta.length() > 0) {
                meta.append(" · ");
            }
            meta.append(Dates.shortDate(requireContext(), row.getDate()));
        }
        if (row.getEmployee() != null && !row.getEmployee().trim().isEmpty()) {
            meta.append(" · ").append(row.getEmployee().trim());
        }
        if (row.getCustomer() != null && !row.getCustomer().trim().isEmpty()) {
            meta.append(" · ").append(row.getCustomer().trim());
        }
        return meta.toString();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}