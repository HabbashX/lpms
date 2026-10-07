package com.lpms.ui.customers;

import android.os.Bundle;
import android.text.InputType;
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
import com.lpms.data.dto.CustomerAccountResponse;
import com.lpms.data.dto.CustomerResponse;
import com.lpms.data.dto.PaymentMethod;
import com.lpms.data.dto.TransactionResponse;
import com.lpms.databinding.FragmentCustomerDetailBinding;
import com.lpms.databinding.ItemDetailRowBinding;
import com.lpms.databinding.ItemTransactionBinding;
import com.lpms.ui.common.FormError;

import java.math.BigDecimal;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Customer detail and statement.
 *
 * <p>This is where the balance lives: {@code GET /customers/{id}/account} returns the
 * totals together with a page of transactions, so one request fills the whole screen.</p>
 */
@AndroidEntryPoint
public final class CustomerDetailFragment extends Fragment {
    private FragmentCustomerDetailBinding binding;
    private CustomerDetailViewModel viewModel;
    private StateRenderer stateRenderer;
    private String currencySymbol;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCustomerDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(CustomerDetailViewModel.class);
        currencySymbol = new AppPreferences(requireContext()).currencySymbol();

        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());

        stateRenderer = new StateRenderer(binding.state);
        stateRenderer.setRetryListener(viewModel::load);

        binding.swipeRefresh.setOnRefreshListener(() -> viewModel.refresh());
        binding.recordPayment.setOnClickListener(v -> showPaymentDialog());
        binding.adjust.setOnClickListener(v -> showAdjustmentDialog());
        binding.moreTransactions.setOnClickListener(v -> viewModel.loadMoreTransactions());

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.refreshing().observe(getViewLifecycleOwner(), refreshing ->
                binding.swipeRefresh.setRefreshing(Boolean.TRUE.equals(refreshing)));
        viewModel.busy().observe(getViewLifecycleOwner(), busy -> {
            boolean working = Boolean.TRUE.equals(busy);
            binding.busy.setVisibility(working ? View.VISIBLE : View.GONE);
            // A second tap while a write is in flight would post the payment twice.
            binding.recordPayment.setEnabled(!working);
            binding.adjust.setEnabled(!working);
        });
        viewModel.message().observe(getViewLifecycleOwner(), issue -> {
            if (issue == null) {
                return;
            }
            Snackbar.make(binding.getRoot(), issue.resolve(requireContext()),
                    Snackbar.LENGTH_LONG).show();
        });
        viewModel.finished().observe(getViewLifecycleOwner(), done -> {
            if (done == null) {
                return;
            }
            // Re-read so the new balance is what the cashier sees, not the old one.
            viewModel.load();
        });

        viewModel.load();
    }

    private void render(@Nullable UiState<CustomerAccountResponse> state) {
        stateRenderer.render(state, binding.content);
        if (state == null || !state.isContent()) {
            return;
        }
        CustomerAccountResponse account = state.valueOrNull();
        if (account == null) {
            return;
        }
        bindIdentity(account.getCustomer());
        bindSummary(account);
        bindTransactions();
    }

    private void bindIdentity(@Nullable CustomerResponse customer) {
        if (customer == null) {
            binding.name.setText("");
            binding.subtitle.setText("");
            return;
        }
        binding.name.setText(customer.getName() == null ? "" : customer.getName());

        StringBuilder subtitle = new StringBuilder();
        append(subtitle, customer.getPhone());
        append(subtitle, customer.getAddress());
        binding.subtitle.setText(subtitle.toString());
        binding.subtitle.setVisibility(subtitle.length() == 0 ? View.GONE : View.VISIBLE);

        String notes = customer.getNotes();
        boolean hasNotes = notes != null && !notes.trim().isEmpty();
        binding.notes.setVisibility(hasNotes ? View.VISIBLE : View.GONE);
        if (hasNotes) {
            binding.notes.setText(notes.trim());
        }

        // A deactivated customer cannot be used for a new sale, so say so on their record.
        binding.inactiveBanner.setVisibility(
                customer.isActive() ? View.VISIBLE : View.GONE);
    }

    private void bindSummary(@NonNull CustomerAccountResponse account) {
        binding.summaryRows.removeAllViews();
        addRow(getString(R.string.customer_total_purchases),
                Money.format(account.getTotalPurchases(), currencySymbol));
        addRow(getString(R.string.customer_total_paid),
                Money.format(account.getTotalPaid(), currencySymbol));
        addRow(getString(R.string.customer_total_refunds),
                Money.format(account.getTotalRefunds(), currencySymbol));
        // The outstanding balance, emphasised because it is the point of this screen.
        addRow(getString(R.string.customer_current_debt),
                Money.format(account.getCurrentDebt(), currencySymbol), true);
    }

    private void bindTransactions() {
        binding.transactions.removeAllViews();
        List<TransactionResponse> rows = viewModel.transactions();
        boolean none = rows == null || rows.isEmpty();
        binding.transactionsHint.setVisibility(none ? View.VISIBLE : View.GONE);
        binding.moreTransactions.setVisibility(
                !none && viewModel.hasMoreTransactions() ? View.VISIBLE : View.GONE);

        if (none) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (TransactionResponse transaction : rows) {
            ItemTransactionBinding row = ItemTransactionBinding.inflate(
                    inflater, binding.transactions, false);
            bindTransaction(row, transaction);
            binding.transactions.addView(row.getRoot());
        }
    }

    private void bindTransaction(@NonNull ItemTransactionBinding row,
                                 @NonNull TransactionResponse transaction) {
        row.type.setText(transaction.getDescription() == null
                ? getString(R.string.customer_transaction_unknown)
                : transaction.getDescription());

        // debit and credit are mutually exclusive: a sale debits the account, a payment
        // credits it. Only one is ever set, so the sign follows from which is present.
        boolean credit = transaction.getCredit() != null
                && transaction.getCredit().signum() != 0;
        BigDecimal amount = credit ? transaction.getCredit() : transaction.getDebit();

        row.amount.setText((credit ? "+" : "-") + Money.format(amount, currencySymbol));

        StringBuilder meta = new StringBuilder(
                Dates.dateTime(requireContext(), transaction.getDate()));
        if (transaction.getCreatedBy() != null && !transaction.getCreatedBy().trim().isEmpty()) {
            meta.append(" · ").append(transaction.getCreatedBy().trim());
        }
        row.date.setText(meta.toString());

        boolean hasBalance = transaction.getBalance() != null;
        row.balance.setVisibility(hasBalance ? View.VISIBLE : View.GONE);
        if (hasBalance) {
            row.balance.setText(getString(R.string.customer_transaction_balance,
                    Money.format(transaction.getBalance(), currencySymbol)));
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

    // ------------------------------------------------------------------- actions

    private void showPaymentDialog() {
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_amount, null, false);
        EditText amount = content.findViewById(R.id.amount);
        EditText notes = content.findViewById(R.id.notes);

        String[] methods = new String[PaymentMethod.values().length];
        for (int i = 0; i < methods.length; i++) {
            methods[i] = PaymentMethod.values()[i].name();
        }
        // Cash first: it is what a counter payment is nearly always.
        final int[] selected = {0};

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.customer_record_payment)
                .setView(content)
                .setNeutralButton(R.string.customer_payment_method, (d, which) ->
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle(R.string.customer_payment_method)
                                .setItems(methods, (inner, index) -> selected[0] = index)
                                .setPositiveButton(R.string.action_ok, null)
                                .show())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, (dialog, which) ->
                        viewModel.recordPayment(
                                amount.getText().toString(),
                                PaymentMethod.values()[selected[0]].wireValue(),
                                notes.getText().toString()))
                .show();
    }

    private void showAdjustmentDialog() {
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_adjustment, null, false);
        EditText amount = content.findViewById(R.id.amount);
        EditText description = content.findViewById(R.id.description);
        final boolean[] credit = {false};

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.customer_adjust)
                .setView(content)
                .setNeutralButton(R.string.customer_adjust_switch, (d, which) -> {
                    credit[0] = !credit[0];
                    toast(credit[0] ? R.string.customer_adjust_credit
                            : R.string.customer_adjust_debit);
                })
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, (dialog, which) ->
                        viewModel.adjust(amount.getText().toString(), credit[0],
                                description.getText().toString()))
                .show();
    }

    private void toast(int messageRes) {
        Snackbar.make(binding.getRoot(), messageRes, Snackbar.LENGTH_SHORT).show();
    }

    private void append(@NonNull StringBuilder target, @Nullable String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        if (target.length() > 0) {
            target.append(" · ");
        }
        target.append(trimmed);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}