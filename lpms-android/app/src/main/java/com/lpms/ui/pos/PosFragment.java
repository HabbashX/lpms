package com.lpms.ui.pos;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.lpms.R;
import com.lpms.core.util.AppPreferences;
import com.lpms.core.util.Money;
import com.lpms.data.dto.CustomerResponse;
import com.lpms.data.dto.DrugResponse;
import com.lpms.data.dto.PaymentMethod;
import com.lpms.data.dto.SaleResponse;
import com.lpms.databinding.FragmentPosBinding;
import com.lpms.databinding.ItemDetailRowBinding;
import com.lpms.domain.cart.CartLine;
import com.lpms.domain.cart.CartTotals;
import com.lpms.ui.pos.scan.BarcodeScanner;
import com.lpms.ui.sales.SaleDetailFragment;

import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Point of sale.
 *
 * <p>The confirm button is disabled whenever the cart is invalid <b>or</b> a request is
 * in flight, because the backend has no idempotency key: a second tap would create a
 * second sale.</p>
 */
@AndroidEntryPoint
public final class PosFragment extends Fragment implements CartAdapter.Listener {

    private FragmentPosBinding binding;
    private PosViewModel viewModel;
    private CartAdapter cartAdapter;
    private DrugsSearchAdapter searchAdapter;
    private String currencySymbol = AppPreferences.CURRENCY_DEFAULT;

    /** Suppresses the text watchers while the ViewModel pushes computed values back. */
    private boolean suppressWatchers;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPosBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(PosViewModel.class);
        currencySymbol = new AppPreferences(requireContext()).currencySymbol();

        binding.toolbar.setTitle(R.string.nav_pos);
        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());

        setUpLists();
        setUpInputs();
        observe();
    }

    private void setUpLists() {
        cartAdapter = new CartAdapter(this, currencySymbol);
        binding.cartList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.cartList.setAdapter(cartAdapter);

        searchAdapter = new DrugsSearchAdapter(this::addDrug, currencySymbol);
        binding.searchResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.searchResults.setAdapter(searchAdapter);
    }

    private void setUpInputs() {
        binding.search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!suppressWatchers) {
                    viewModel.searchDrugs(s == null ? "" : s.toString());
                }
            }
        });

        binding.scanButton.setOnClickListener(v ->
                BarcodeScanner.launch(this, barcode -> viewModel.addByBarcode(barcode)));

        binding.pickCustomer.setOnClickListener(v -> showCustomerPicker());
        binding.clearCustomer.setOnClickListener(v -> viewModel.clearCustomer());

        binding.discount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!suppressWatchers) {
                    viewModel.setDiscount(text(binding.discount));
                }
            }
        });

        binding.amountPaid.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!suppressWatchers) {
                    viewModel.setAmountPaid(text(binding.amountPaid));
                }
            }
        });

        binding.paymentChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            int chipId = checkedIds.isEmpty() ? R.id.chip_cash : checkedIds.get(0);
            viewModel.setPaymentMethod(methodFor(chipId));
        });

        binding.confirmSale.setOnClickListener(v -> confirm());
    }

    @NonNull
    private PaymentMethod methodFor(int chipId) {
        if (chipId == R.id.chip_card) {
            return PaymentMethod.CARD;
        }
        if (chipId == R.id.chip_credit) {
            return PaymentMethod.CREDIT;
        }
        if (chipId == R.id.chip_bank_transfer) {
            return PaymentMethod.BANK_TRANSFER;
        }
        if (chipId == R.id.chip_insurance) {
            return PaymentMethod.INSURANCE;
        }
        return PaymentMethod.CASH;
    }

    private int chipFor(PaymentMethod method) {
        switch (method) {
            case CARD:
                return R.id.chip_card;
            case CREDIT:
                return R.id.chip_credit;
            case BANK_TRANSFER:
                return R.id.chip_bank_transfer;
            case INSURANCE:
                return R.id.chip_insurance;
            case CASH:
            default:
                return R.id.chip_cash;
        }
    }

    private void observe() {
        viewModel.cart().observe(getViewLifecycleOwner(), cart -> {
            cartAdapter.submitList(cart.lines());
            binding.cartEmpty.setVisibility(cart.isEmpty() ? View.VISIBLE : View.GONE);
            binding.cartHeader.setText(getString(R.string.pos_cart_count,
                    cart.lineCount(), cart.totalQuantity()));
            if (cart.getCustomerId() == null) {
                binding.pickCustomer.setText(R.string.pos_pick_customer);
                binding.clearCustomer.setVisibility(View.GONE);
            } else {
                binding.pickCustomer.setText(cart.getCustomerName() == null
                        ? getString(R.string.pos_customer_selected) : cart.getCustomerName());
                binding.clearCustomer.setVisibility(View.VISIBLE);
            }
        });

        viewModel.summary().observe(getViewLifecycleOwner(), this::renderSummary);
        viewModel.submitting().observe(getViewLifecycleOwner(), this::renderSubmitState);
viewModel.uncertainSubmit().observe(getViewLifecycleOwner(), uncertain -> {
            boolean shown = Boolean.TRUE.equals(uncertain);
            binding.uncertainWarning.setVisibility(shown ? View.VISIBLE : View.GONE);
            // The warning tells the cashier to check Sales history; this is that button.
            // POST /sales has no idempotency key, so this is the only safe way to find out
            // whether the sale landed before trying again.
            binding.uncertainViewSales.setOnClickListener(v -> openSalesHistory());
        });

        viewModel.searchResults().observe(getViewLifecycleOwner(), results -> {
            searchAdapter.submitList(results);
            boolean visible = results != null && !results.isEmpty();
            binding.searchResults.setVisibility(visible ? View.VISIBLE : View.GONE);
            binding.searchResultsHeader.setVisibility(visible ? View.VISIBLE : View.GONE);
        });

        viewModel.toast().observe(getViewLifecycleOwner(), issue -> {
            if (issue == null) {
                return;
            }
            Snackbar.make(binding.getRoot(), issue.resolve(requireContext()),
                    Snackbar.LENGTH_LONG).show();
        });

        viewModel.completedSale().observe(getViewLifecycleOwner(), this::openReceipt);
    }

    private void renderSummary(@Nullable PosViewModel.Summary summary) {
        if (summary == null || binding == null) {
            return;
        }
        CartTotals totals = summary.getTotals();
        binding.totals.removeAllViews();
        addTotalRow(R.string.pos_subtotal, Money.format(totals.getSubtotal(), currencySymbol), false);
        addTotalRow(R.string.pos_discount, Money.format(totals.getDiscount(), currencySymbol), false);
        addTotalRow(R.string.pos_total, Money.format(totals.getTotal(), currencySymbol), true);
        addTotalRow(R.string.pos_due, Money.format(totals.getAmountDue(), currencySymbol), true);

        List<String> problems = summary.getProblems();
        if (problems.isEmpty()) {
            binding.validationMessage.setVisibility(View.GONE);
        } else {
            binding.validationMessage.setVisibility(View.VISIBLE);
            binding.validationMessage.setText(String.join("\n", problems));
        }

        binding.paymentChips.check(chipFor(summary.getCart().getPaymentMethod()));
        binding.amountPaidLayout.setHint(summary.getCart().getPaymentMethod()
                == PaymentMethod.CREDIT
                ? R.string.pos_amount_paid_credit
                : R.string.pos_amount_paid);

        renderSubmitState(viewModel.submitting().getValue());
    }

    private void addTotalRow(int labelRes, @NonNull String value, boolean emphasise) {
        ItemDetailRowBinding row = ItemDetailRowBinding.inflate(
                LayoutInflater.from(requireContext()), binding.totals, false);
        row.label.setText(labelRes);
        row.value.setText(value);
        if (emphasise) {
            row.value.setTypeface(row.value.getTypeface(), android.graphics.Typeface.BOLD);
        }
        binding.totals.addView(row.getRoot());
    }

    private void renderSubmitState(@Nullable Boolean submitting) {
        if (binding == null) {
            return;
        }
        boolean busy = Boolean.TRUE.equals(submitting);
        PosViewModel.Summary summary = viewModel.summary().getValue();
        boolean valid = summary != null && summary.canSubmit();
        // Disabled while the POST is in flight: no idempotency key means no double sale.
        binding.confirmSale.setEnabled(valid && !busy);
        binding.confirmSale.setText(busy ? R.string.pos_saving : R.string.pos_confirm_sale);
        binding.paymentChips.setEnabled(!busy);
        binding.pickCustomer.setEnabled(!busy);
        binding.discountLayout.setEnabled(!busy);
        binding.amountPaidLayout.setEnabled(!busy);
    }

    private void confirm() {
        PosViewModel.Summary summary = viewModel.summary().getValue();
        if (summary == null || !summary.canSubmit()) {
            return;
        }
        CartTotals totals = summary.getTotals();
        StringBuilder details = new StringBuilder();
        for (CartLine line : summary.getCart().lines()) {
            details.append("• ").append(line.getQuantity()).append(" × ")
                    .append(line.getDrugName()).append("  ")
                    .append(Money.format(line.lineTotal(), currencySymbol))
                    .append('\n');
        }
        details.append(getString(R.string.pos_due)).append(": ")
                .append(Money.format(totals.getAmountDue(), currencySymbol));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.pos_confirm_title,
                        Money.format(totals.getTotal(), currencySymbol)))
                .setMessage(details.toString())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_confirm, (dialog, which) ->
                        viewModel.confirmSale())
                .show();
    }

    private void openReceipt(@Nullable SaleResponse sale) {
        if (sale == null || sale.getId() == null) {
            return;
        }
        // Stop observing so returning to the POS does not reopen the receipt.
        viewModel.completedSale().removeObservers(getViewLifecycleOwner());
        Bundle args = new Bundle();
        args.putLong(SaleDetailFragment.ARG_SALE_ID, sale.getId());
        NavHostFragment.findNavController(this).navigate(R.id.action_pos_to_saleDetail, args);
    }

    /** Opens the sales history, which is where an unconfirmed sale can be found. */
    private void openSalesHistory() {
        NavHostFragment.findNavController(this).navigate(R.id.salesListFragment);
    }

    private void showCustomerPicker() {
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_customer_picker, null, false);
        EditText input = content.findViewById(R.id.search);
        ListView list = content.findViewById(R.id.results);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pos_pick_customer)
                .setView(content)
                .setNegativeButton(R.string.action_cancel, null)
                .create();

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String query = s == null ? "" : s.toString();
                if (query.length() < 2) {
                    list.setAdapter(null);
                    return;
                }
                viewModel.searchCustomers(query);
            }
        });

        viewModel.customerResults().observe(getViewLifecycleOwner(), customers ->
                list.setAdapter(new CustomerRowAdapter(requireContext(), customers, picked -> {
                    viewModel.setCustomer(picked);
                    dialog.dismiss();
                })));

        dialog.show();
    }

    private void addDrug(@NonNull DrugResponse drug) {
        viewModel.addDrug(drug);
        suppressWatchers = true;
        binding.search.setText("");
        suppressWatchers = false;
        binding.searchResults.setVisibility(View.GONE);
        binding.searchResultsHeader.setVisibility(View.GONE);
    }

    // ------------------------------------------------------- CartAdapter.Listener

    @Override
    public void onQuantityChanged(@NonNull CartLine line, int newQuantity) {
        viewModel.setQuantity(line.getDrugId(), newQuantity);
    }

    @Override
    public void onPriceChanged(@NonNull CartLine line, @Nullable String priceText) {
        viewModel.setLinePrice(line.getDrugId(), priceText);
    }

    @Override
    public void onRemove(@NonNull CartLine line) {
        viewModel.removeLine(line.getDrugId());
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

    /** Two-line list used inside the customer picker dialog. */
    private static final class CustomerRowAdapter extends android.widget.ArrayAdapter<CustomerResponse> {

        private final android.view.View.OnClickListener onPick;

        CustomerRowAdapter(@NonNull android.content.Context context,
                           @NonNull List<CustomerResponse> items,
                           @NonNull java.util.function.Consumer<CustomerResponse> onPick) {
            super(context, android.R.layout.simple_list_item_2, items);
            this.onPick = v -> {
                Object tag = v.getTag();
                int position = tag == null ? -1 : (int) tag;
                if (position >= 0 && position < getCount()) {
                    onPick.accept(getItem(position));
                }
            };
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            View view = convertView != null ? convertView : LayoutInflater.from(getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            CustomerResponse customer = getItem(position);
            ((TextView) view.findViewById(android.R.id.text1))
                    .setText(customer.getName() == null ? "" : customer.getName());
            ((TextView) view.findViewById(android.R.id.text2))
                    .setText(customer.getPhone() == null ? "" : customer.getPhone());
            view.setTag(position);
            view.setOnClickListener(onPick);
            return view;
        }
    }
}