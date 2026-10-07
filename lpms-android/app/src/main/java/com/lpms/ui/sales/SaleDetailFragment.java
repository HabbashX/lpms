package com.lpms.ui.sales;

import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
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
import com.lpms.data.dto.SaleItemResponse;
import com.lpms.data.dto.SaleResponse;
import com.lpms.data.dto.SaleStatus;
import com.lpms.databinding.FragmentSaleDetailBinding;
import com.lpms.databinding.ItemDetailRowBinding;


import dagger.hilt.android.AndroidEntryPoint;

/**
 * Sale receipt and detail.
 *
 * <p>Cost and profit rows are rendered only for ADMIN/PHARMACIST — the backend sends them
 * to every role, so hiding them is the client's job. The receipt can be printed through
 * the Android print framework (PDF) or shared as plain text.</p>
 */
@AndroidEntryPoint
public final class SaleDetailFragment extends Fragment {

    /** Navigation argument: sale id. */
    public static final String ARG_SALE_ID = "saleId";

    private FragmentSaleDetailBinding binding;
    private SaleDetailViewModel viewModel;
    private StateRenderer stateRenderer;
    private SaleResponse current;
    private String currencySymbol = AppPreferences.CURRENCY_DEFAULT;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSaleDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SaleDetailViewModel.class);
        currencySymbol = new AppPreferences(requireContext()).currencySymbol();

        binding.toolbar.setTitle(R.string.nav_sales);
        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());

        stateRenderer = new StateRenderer(binding.state);
        stateRenderer.setRetryListener(viewModel::load);

        binding.printButton.setOnClickListener(v -> printReceipt());
        binding.shareButton.setOnClickListener(v -> shareReceipt());
        binding.refundButton.setVisibility(viewModel.canRefund() ? View.VISIBLE : View.GONE);
        binding.refundButton.setOnClickListener(v -> startRefund());

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.refunding().observe(getViewLifecycleOwner(), busy -> {
            binding.refundButton.setEnabled(!Boolean.TRUE.equals(busy));
            binding.refundButton.setText(Boolean.TRUE.equals(busy)
                    ? R.string.sale_refunding : R.string.sale_refund);
        });
        viewModel.toast().observe(getViewLifecycleOwner(), issue -> {
            if (issue == null) {
                return;
            }
            Snackbar.make(binding.getRoot(), issue.resolve(requireContext()),
                    Snackbar.LENGTH_LONG).show();
        });
        viewModel.refunded().observe(getViewLifecycleOwner(), refund -> {
            if (refund == null) {
                return;
            }
            Snackbar.make(binding.getRoot(),
                    getString(R.string.sale_refunded,
                            Money.format(refund.getTotalAmount(), currencySymbol)),
                    Snackbar.LENGTH_LONG).show();
        });

        viewModel.load();
    }

    private void render(@Nullable UiState<SaleResponse> state) {
        stateRenderer.render(state, binding.content);
        if (state == null || !state.isContent()) {
            return;
        }
        SaleResponse sale = state.valueOrNull();
        if (sale == null) {
            return;
        }
        current = sale;
        bind(sale);
    }

    private void bind(@NonNull SaleResponse sale) {
        String currency = currencySymbol;
        binding.toolbar.setTitle(getString(R.string.sale_number, sale.getId()));
        binding.date.setText(Dates.dateTime(requireContext(), sale.getCreatedAt()));
        binding.cashier.setText(getString(R.string.sale_by,
                sale.getCreatedBy() == null ? "" : sale.getCreatedBy()));

        SaleStatus status = sale.saleStatus();
        binding.statusBadge.setText(labelFor(status));
        binding.statusBadge.setVisibility(View.VISIBLE);
        binding.statusBadge.setBackgroundResource(status == SaleStatus.COMPLETED
                ? R.drawable.bg_badge_ok
                : R.drawable.bg_badge_low);

        binding.rows.removeAllViews();
        // bind() runs on every state emission (including after a refund), so all
        // programmatically built sections must be cleared before they are refilled.
        binding.totals.removeAllViews();
        binding.profitRows.removeAllViews();
        for (SaleItemResponse item : sale.getItems()) {
            addRow(item.getDrugName(), item.getQuantity() + " × "
                    + Money.format(item.getUnitSellingPrice(), currency) + "  =  "
                    + Money.format(item.getRevenue(), currency));
            if (viewModel.canSeeProfit() && item.getProfit() != null) {
                addRow(getString(R.string.sale_line_profit),
                        Money.format(item.getProfit(), currency));
            }
        }

        addTotal(getString(R.string.pos_subtotal), Money.format(sale.getSubtotal(), currency));
        addTotal(getString(R.string.pos_discount), Money.format(sale.getDiscount(), currency));
        addTotal(getString(R.string.pos_total), Money.format(sale.getTotal(), currency));
        addTotal(getString(R.string.sale_paid), Money.format(sale.getAmountPaid(), currency));
        addTotal(getString(R.string.pos_due), Money.format(sale.getAmountDue(), currency));

        // Cost/profit only for ADMIN/PHARMACIST.
        binding.profitRows.setVisibility(
                viewModel.canSeeProfit() ? View.VISIBLE : View.GONE);
        if (viewModel.canSeeProfit()) {
            binding.profitRows.removeAllViews();
            addTotal(getString(R.string.sale_cost), Money.format(sale.getCost(), currency));
            addTotal(getString(R.string.sale_profit), Money.format(sale.getProfit(), currency));
        }

        if (sale.hasRefunds()) {
            binding.refundedTotal.setVisibility(View.VISIBLE);
            binding.refundedTotal.setText(getString(R.string.sale_refunded_total,
                    Money.format(sale.getRefundedTotal(), currency)));
        } else {
            binding.refundedTotal.setVisibility(View.GONE);
        }

        boolean refundable = viewModel.canRefund() && status.isRefundable();
        binding.refundButton.setVisibility(refundable ? View.VISIBLE : View.GONE);
    }

    private void addRow(@NonNull String label, @NonNull String value) {
        ItemDetailRowBinding row = ItemDetailRowBinding.inflate(
                LayoutInflater.from(requireContext()), binding.rows, false);
        row.label.setText(label);
        row.value.setText(value);
        binding.rows.addView(row.getRoot());
    }

    private void addTotal(@NonNull String label, @NonNull String value) {
        ItemDetailRowBinding row = ItemDetailRowBinding.inflate(
                LayoutInflater.from(requireContext()), binding.totals, false);
        row.label.setText(label);
        row.value.setText(value);
        row.value.setTypeface(row.value.getTypeface(), android.graphics.Typeface.BOLD);
        binding.totals.addView(row.getRoot());
    }

    @NonNull
    private String labelFor(SaleStatus status) {
        if (status == SaleStatus.REFUNDED) {
            return getString(R.string.sale_status_refunded);
        }
        if (status == SaleStatus.PARTIALLY_REFUNDED) {
            return getString(R.string.sale_status_partially_refunded);
        }
        return getString(R.string.sale_status_completed);
    }

    /** Per-line stepper capped at {@code quantity − refundedQuantity}. */
    private void startRefund() {
        if (current == null) {
            return;
        }
        StringBuilder summary = new StringBuilder();
        for (SaleItemResponse item : current.getItems()) {
            if (item.getId() != null && item.getRefundableQuantity() > 0) {
                summary.append("• ").append(item.getDrugName()).append(" — up to ")
                        .append(item.getRefundableQuantity()).append('\n');
            }
        }
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_refund, null, false);
        com.google.android.material.textfield.TextInputEditText reason =
                content.findViewById(R.id.reason);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.sale_refund)
                .setMessage(getString(R.string.sale_refund_explain,
                        summary.toString().trim()))
                .setView(content)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_confirm, (dialog, which) ->
                        viewModel.submitRefund(reason.getText() == null
                                ? null : reason.getText().toString()))
                .show();
    }

    // ------------------------------------------------------------------- output

    @NonNull
    private String receiptText() {
        if (current == null) {
            return "";
        }
        String currency = currencySymbol;
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.sale_number, current.getId())).append('\n');
        sb.append(Dates.dateTime(requireContext(), current.getCreatedAt())).append('\n');
        sb.append(getString(R.string.sale_payment_method,
                current.paymentMethod().name())).append('\n');
        if (current.getCustomerName() != null) {
            sb.append(getString(R.string.sale_customer, current.getCustomerName())).append('\n');
        }
        sb.append('\n');
        for (SaleItemResponse item : current.getItems()) {
            sb.append(item.getQuantity()).append(" × ")
                    .append(item.getDrugName())
                    .append("  ")
                    .append(Money.format(item.getUnitSellingPrice(), currency))
                    .append("  ")
                    .append(Money.format(item.getRevenue(), currency))
                    .append('\n');
        }
        sb.append('\n');
        sb.append(getString(R.string.pos_subtotal)).append(": ")
                .append(Money.format(current.getSubtotal(), currency)).append('\n');
        sb.append(getString(R.string.pos_discount)).append(": ")
                .append(Money.format(current.getDiscount(), currency)).append('\n');
        sb.append(getString(R.string.pos_total)).append(": ")
                .append(Money.format(current.getTotal(), currency)).append('\n');
        sb.append(getString(R.string.sale_paid)).append(": ")
                .append(Money.format(current.getAmountPaid(), currency)).append('\n');
        sb.append(getString(R.string.pos_due)).append(": ")
                .append(Money.format(current.getAmountDue(), currency)).append('\n');
        return sb.toString();
    }

    /** Android print framework → PDF, using a one-page canvas of the receipt text. */
    private void printReceipt() {
        PrintManager printManager =
                ContextCompat.getSystemService(requireContext(), PrintManager.class);
        if (printManager == null || current == null) {
            return;
        }
        final String text = receiptText();
        String jobName = getString(R.string.sale_number, current.getId());

        printManager.print(jobName, new android.print.PrintDocumentAdapter() {

            @NonNull
            @Override
            public void onLayout(@Nullable PrintAttributes oldAttributes,
                                 @Nullable PrintAttributes newAttributes,
                                 @Nullable CancellationSignal cancellationSignal,
                                 @NonNull LayoutResultCallback callback,
                                 @NonNull android.os.Bundle extras) {
                PrintDocumentInfo info = new PrintDocumentInfo.Builder("lpms-receipt")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1)
                        .build();
                callback.onLayoutFinished(info, true);
            }

            @Override
            public void onWrite(@NonNull PageRange[] pages,
                                @NonNull ParcelFileDescriptor destination,
                                @Nullable CancellationSignal cancellationSignal,
                                @NonNull WriteResultCallback callback) {
                try (android.os.ParcelFileDescriptor.AutoCloseOutputStream stream =
                             new android.os.ParcelFileDescriptor.AutoCloseOutputStream(destination)) {
                    android.graphics.pdf.PdfDocument document =
                            new android.graphics.pdf.PdfDocument();
                    android.graphics.pdf.PdfDocument.Page page = document.startPage(
                            new android.graphics.pdf.PdfDocument.PageInfo.Builder(0, 1240, 595)
                                    .create());
                    android.graphics.Paint paint = new android.graphics.Paint();
                    paint.setTextSize(28f);
                    page.getCanvas().drawText(text, 40f, 60f, paint);
                    document.finishPage(page);
                    document.writeTo(stream);
                    document.close();
                    callback.onWriteFinished(new PageRange[]{new PageRange(0, 0)});
                } catch (Exception e) {
                    callback.onWriteFailed(e.getMessage());
                }
            }
        }, new PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.UNKNOWN_PORTRAIT)
                .build());
    }

    private void shareReceipt() {
        android.content.Intent intent = new android.content.Intent(
                android.content.Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(android.content.Intent.EXTRA_TEXT, receiptText());
        startActivity(android.content.Intent.createChooser(intent,
                getString(R.string.sale_share)));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        current = null;
    }
}
