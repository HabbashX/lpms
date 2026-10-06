package com.lpms.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.lpms.R;
import com.lpms.core.ui.ErrorPresenter;
import com.lpms.core.ui.StateRenderer;
import com.lpms.core.ui.UiState;
import com.lpms.core.util.AppPreferences;
import com.lpms.core.util.Dates;
import com.lpms.core.util.Money;
import com.lpms.data.dto.DashboardResponse;
import com.lpms.databinding.FragmentDashboardBinding;
import com.lpms.ui.common.MetricTile;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Home screen.
 *
 * <p>Tiles for today/month, inventory value, low-stock and expiring counts (tap through
 * to those screens), customers + total debt, and the top-selling list. Profit tiles are
 * hidden for EMPLOYEE because the endpoint returns them to every role.</p>
 */
@AndroidEntryPoint
public final class DashboardFragment extends Fragment {

    private FragmentDashboardBinding binding;
    private DashboardViewModel viewModel;
    private StateRenderer stateRenderer;
    private TopDrugsAdapter topDrugsAdapter;
    private String currencySymbol = "$";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
        currencySymbol = new AppPreferences(requireContext()).currencySymbol();

        stateRenderer = new StateRenderer(binding.state);
        stateRenderer.setRetryListener(viewModel::load);

        topDrugsAdapter = new TopDrugsAdapter(currencySymbol);
        binding.topDrugsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.topDrugsList.setAdapter(topDrugsAdapter);
        binding.topDrugsList.setNestedScrollingEnabled(false);

        binding.swipeRefresh.setOnRefreshListener(viewModel::refresh);

        // Tile navigation (low stock → inventory, expiring → inventory, customers →
        // customer list) is wired once those destinations exist in the nav graph.

        binding.headerSubtitle.setText(greeting());

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.refreshing().observe(getViewLifecycleOwner(), refreshing ->
                binding.swipeRefresh.setRefreshing(Boolean.TRUE.equals(refreshing)));

        viewModel.load();
    }

    private void render(@Nullable UiState<DashboardResponse> state) {
        stateRenderer.render(state, binding.content);
        if (state == null || !state.isContent()) {
            return;
        }
        DashboardResponse data = state.valueOrNull();
        if (data == null) {
            return;
        }
        bindTiles(data);
        bindTopDrugs(data);
    }

    private void bindTiles(@NonNull DashboardResponse data) {
        DashboardResponse.PeriodSummary today = data.today();
        DashboardResponse.PeriodSummary month = data.month();
        DashboardResponse.InventorySummary inventory = data.inventory();
        DashboardResponse.CustomerSummary customers = data.customers();

        MetricTile.update(binding.tileTodayRevenue,
                Money.format(today.getRevenue(), currencySymbol),
                getString(R.string.dashboard_sales_count, today.getSales()));

        MetricTile.update(binding.tileMonthRevenue,
                Money.format(month.getRevenue(), currencySymbol),
                monthLabel(month));

        // Profit is returned to every role; the UI decides who may see it.
        boolean showProfit = viewModel.canSeeProfit();
        binding.tileTodayProfit.getRoot().setVisibility(showProfit ? View.VISIBLE : View.GONE);
        binding.tileMonthProfit.getRoot().setVisibility(showProfit ? View.VISIBLE : View.GONE);
        if (showProfit) {
            MetricTile.update(binding.tileTodayProfit,
                    Money.format(today.getProfit(), currencySymbol),
                    getString(R.string.dashboard_profit_today));
            MetricTile.update(binding.tileMonthProfit,
                    Money.format(month.getProfit(), currencySymbol),
                    getString(R.string.dashboard_profit_month));
        }

        MetricTile.update(binding.tileInventoryValue,
                Money.format(inventory.getValue(), currencySymbol),
                getString(R.string.dashboard_stock_value));

        MetricTile.update(binding.tileLowStock,
                String.valueOf(inventory.getLowStock()),
                getString(R.string.nav_low_stock));

        MetricTile.update(binding.tileExpiring,
                String.valueOf(inventory.getExpiringSoon()),
                getString(R.string.nav_expiring));

        MetricTile.update(binding.tileCustomers,
                String.valueOf(customers.getCount()),
                getString(R.string.dashboard_total_debt,
                        Money.format(customers.getTotalDebt(), currencySymbol)));
    }

    private void bindTopDrugs(@NonNull DashboardResponse data) {
        topDrugsAdapter.submitList(data.getTopSellingDrugs());
        boolean empty = data.getTopSellingDrugs().isEmpty();
        binding.topDrugsEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.topDrugsList.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @NonNull
    private String monthLabel(@NonNull DashboardResponse.PeriodSummary month) {
        if (month.getYear() == null || month.getMonth() == null) {
            return getString(R.string.dashboard_this_month);
        }
        return Dates.date(requireContext(),
                java.time.LocalDate.of(month.getYear(), month.getMonth(), 1))
                + " · " + getString(R.string.dashboard_sales_count, month.getSales());
    }

    @NonNull
    private String greeting() {
        java.time.LocalTime now = java.time.LocalTime.now(Dates.clock());
        if (now.getHour() < 12) {
            return getString(R.string.dashboard_greeting_morning);
        }
        if (now.getHour() < 18) {
            return getString(R.string.dashboard_greeting_afternoon);
        }
        return getString(R.string.dashboard_greeting_evening);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}