package com.lpms.ui.pos;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
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
import com.lpms.data.dto.TransferDetails;
import com.lpms.data.dto.TransferProvider;
import com.lpms.databinding.DialogBankTransferBinding;
import com.lpms.databinding.FragmentPosBinding;
import com.lpms.databinding.ItemDetailRowBinding;
import com.lpms.domain.cart.CartLine;
import com.lpms.domain.cart.CartTotals;
import com.lpms.ui.pos.scan.BarcodeScanner;
import com.lpms.ui.sales.SaleDetailFragment;

import java.math.BigDecimal;
import java.util.ArrayList;
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
    /** Guards against the chip listener re-opening the dialog during {@code render}. */
    private boolean transferDialogShowing = false;
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
            PaymentMethod method = methodFor(chipId);
            viewModel.setPaymentMethod(method);
            // Bank transfer money cannot be traced without a destination, so ask for one
            // at the moment it is chosen rather than failing at confirmation.
            if (method == PaymentMethod.BANK_TRANSFER) {
                showTransferDialog();
            }
        });

        binding.transferRow.setOnClickListener(v -> showTransferDialog());

        binding.confirmSale.setOnClickListener(v -> confirm());
    }

    /**
     * Asks where a bank transfer payment is going: provider, account name, and the
     * identifier that provider actually uses.
     *
     * <p>Cancelling leaves any previously recorded destination untouched, so a mis-tap on
     * the chip cannot silently discard details the cashier already entered.</p>
     */
    private void showTransferDialog() {
        if (binding == null || transferDialogShowing) {
            return;
        }
        DialogBankTransferBinding dialog =
                DialogBankTransferBinding.inflate(getLayoutInflater());
        TransferDetails existing = viewModel.cart().getValue() == null
                ? null : viewModel.cart().getValue().getTransfer();

        List<String> providerNames = new ArrayList<>();
        for (TransferProvider provider : TransferProvider.values()) {
            providerNames.add(provider.displayName());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, providerNames);
        dialog.provider.setAdapter(adapter);

        TransferProvider[] chosen = new TransferProvider[1];
        chosen[0] = existing == null ? null : TransferProvider.fromWire(existing.getProvider());
        if (chosen[0] == null) {
            chosen[0] = TransferProvider.JAWWAL_PAY;
        }
        dialog.provider.setText(chosen[0].displayName(), false);
        applyProviderLabels(dialog, chosen[0]);
        if (existing != null) {
            dialog.accountName.setText(existing.getAccountName());
            dialog.accountIdentifier.setText(existing.getAccountIdentifier());
        }

        dialog.provider.setOnItemClickListener((parent, view, position, id) ->
                applyProviderLabels(dialog, TransferProvider.values()[position]));

        transferDialogShowing = true;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pos_transfer_title)
                .setView(dialog.getRoot())
                .setPositiveButton(R.string.action_save, (v, which) -> {
                    TransferProvider provider = chosen[0];
                    String accountName = text(dialog.accountName).trim();
                    String identifier = text(dialog.accountIdentifier).trim();
                    if (accountName.isEmpty()) {
                        dialog.accountNameLayout.setError(
                                getString(R.string.pos_transfer_missing_account_name));
                        return;
                    }
                    if (identifier.isEmpty()) {
                        dialog.accountIdentifierLayout.setError(getString(
                                R.string.pos_transfer_missing_identifier,
                                provider.identifierLabel().toLowerCase(java.util.Locale.getDefault())));
                        return;
                    }
                    viewModel.setTransfer(new TransferDetails(provider, accountName, identifier));
                })
                .setNegativeButton(R.string.action_cancel, null)
                .setOnDismissListener(d -> transferDialogShowing = false)
                .show();
    }

    /** Retitles the identifier box, because its meaning depends on the provider. */
    private void applyProviderLabels(@NonNull DialogBankTransferBinding dialog,
                                     @NonNull TransferProvider provider) {
        dialog.accountIdentifierLayout.setHint(provider.identifierLabel());
        dialog.accountIdentifierLayout.setHelperText(provider.identifierHint());
        // An email is not a number and a number is not an email; the soft keyboard should
        // match what the cashier is about to type.
        dialog.accountIdentifier.setInputType(provider == TransferProvider.PAYPAL
                ? android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                : android.text.InputType.TYPE_CLASS_TEXT);
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

        // A figure above the total is change owed to the customer, not an error. It is
        // shown prominently because the cashier has to hand it back.
        BigDecimal change = viewModel.changeDue();
        if (change.signum() > 0) {
            addTotalRow(R.string.pos_change, Money.format(change, currencySymbol), true);
        }
        renderTransfer(summary.getCart());

        List<String> problems = summary.getProblems();
        if (problems.isEmpty()) {
            binding.validationMessage.setVisibility(View.GONE);
        } else {
            binding.validationMessage.setVisibility(View.VISIBLE);
            binding.validationMessage.setText(String.join("\n", problems));
        }

        binding.paymentChips.check(chipFor(summary.getCart().getPaymentMethod()));
        // The field is what the customer hands over, not what is retained, so label it that
        // way. Anything above the total comes back as change.
        binding.amountPaidLayout.setHint(summary.getCart().getPaymentMethod()
                == PaymentMethod.CREDIT
                ? R.string.pos_amount_paid_credit
                : R.string.pos_cash_received);

        renderSubmitState(viewModel.submitting().getValue());
    }

    /**
     * Shows the recorded transfer destination under the totals, and offers to edit it.
     *
     * <p>Only meaningful for {@link PaymentMethod#BANK_TRANSFER}, so it is hidden for every
     * other method rather than shown empty.</p>
     */
    private void renderTransfer(@NonNull com.lpms.domain.cart.Cart cart) {
        if (cart.getPaymentMethod() != PaymentMethod.BANK_TRANSFER) {
            binding.transferRow.setVisibility(View.GONE);
            return;
        }
        binding.transferRow.setVisibility(View.VISIBLE);
        TransferDetails transfer = cart.getTransfer();
        if (transfer == null || !transfer.isComplete()) {
            binding.transferValue.setText(R.string.pos_transfer_required);
            return;
        }
        TransferProvider provider =
                TransferProvider.fromWire(transfer.getProvider());
        String providerName = provider == null ? transfer.getProvider() : provider.displayName();
        binding.transferValue.setText(getString(R.string.pos_transfer_summary,
                providerName, transfer.getAccountName(), transfer.getAccountIdentifier()));
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
        // Read any price field still holding focus and commit it, so the sale cannot be
        // submitted with a typed-but-uncommitted override.
        flushFocusedPrice();
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

    /**
     * Commits the price field of whichever row still has focus.
     *
     * <p>Delegated to the adapter, which owns the rows.</p>
     */
    private void flushFocusedPrice() {
        if (cartAdapter != null) {
            cartAdapter.commitFocusedPrice();
        }
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