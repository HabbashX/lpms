package com.lpms.ui.pos;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.R;
import com.lpms.core.error.ApiError;
import com.lpms.core.error.ApiErrorCodes;
import com.lpms.core.network.NetworkCall;
import com.lpms.core.util.Money;
import com.lpms.data.dto.CreateSaleItemRequest;
import com.lpms.data.dto.CustomerResponse;
import com.lpms.data.dto.DrugResponse;
import com.lpms.data.dto.PaymentMethod;
import com.lpms.data.dto.SaleResponse;
import com.lpms.data.dto.TransferDetails;
import com.lpms.data.repo.CustomerRepository;
import com.lpms.data.repo.DrugRepository;
import com.lpms.data.repo.SalesRepository;
import com.lpms.domain.cart.Cart;
import com.lpms.domain.cart.CartLine;
import com.lpms.domain.cart.CartTotals;
import com.lpms.ui.common.FormError;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * Point of sale.
 *
 * <p>Owns the cart, the money preview and the single confirmation that creates the sale.
 * Because the backend has no idempotency key, {@link #confirmSale} refuses to run twice
 * for the same cart and never retries automatically — after a timeout it reports the
 * failure and the cashier must look at the sales list before trying again.</p>
 */
@HiltViewModel
public final class PosViewModel extends ViewModel {

    /** Everything the POS screen renders about money. */
    public static final class Summary {
        final Cart cart;
        final CartTotals totals;
        final List<String> problems;

        Summary(@NonNull Cart cart, @NonNull CartTotals totals, @NonNull List<String> problems) {
            this.cart = cart;
            this.totals = totals;
            this.problems = problems;
        }

        @NonNull
        public Cart getCart() {
            return cart;
        }

        @NonNull
        public CartTotals getTotals() {
            return totals;
        }

        @NonNull
        public List<String> getProblems() {
            return problems;
        }

        public boolean canSubmit() {
            return problems.isEmpty() && !cart.isEmpty();
        }
    }

    private final DrugRepository drugRepository;
    private final CustomerRepository customerRepository;
    private final SalesRepository salesRepository;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<Cart> cart = new MutableLiveData<>(Cart.empty());
    private final MutableLiveData<Summary> summary = new MutableLiveData<>();
    private final MutableLiveData<BigDecimal> amountPaid = new MutableLiveData<>(BigDecimal.ZERO);
    private final MutableLiveData<List<DrugResponse>> searchResults =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<CustomerResponse>> customerResults =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> searching = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MutableLiveData<FormError> message = new MutableLiveData<>();
    private final MutableLiveData<SaleResponse> completedSale = new MutableLiveData<>();

    /** Set once a create attempt fails, to warn about a possible duplicate. */
    private final MutableLiveData<Boolean> uncertainSubmit = new MutableLiveData<>(false);

    @Inject
    public PosViewModel(@NonNull DrugRepository drugRepository,
                        @NonNull CustomerRepository customerRepository,
                        @NonNull SalesRepository salesRepository) {
        this.drugRepository = drugRepository;
        this.customerRepository = customerRepository;
        this.salesRepository = salesRepository;
        recompute();
    }

    // ------------------------------------------------------------- state output

    @NonNull
    public LiveData<Cart> cart() {
        return cart;
    }

    @NonNull
    public LiveData<Summary> summary() {
        return summary;
    }

    @NonNull
    public LiveData<List<DrugResponse>> searchResults() {
        return searchResults;
    }

    @NonNull
    public LiveData<List<CustomerResponse>> customerResults() {
        return customerResults;
    }

    @NonNull
    public LiveData<Boolean> searching() {
        return searching;
    }

    /** True while POST /sales is in flight: the confirm button stays disabled. */
    @NonNull
    public LiveData<Boolean> submitting() {
        return submitting;
    }

    @NonNull
    public LiveData<FormError> toast() {
        return message;
    }

    @NonNull
    public LiveData<SaleResponse> completedSale() {
        return completedSale;
    }

    /** True after a failed create: the sale may or may not exist server-side. */
    @NonNull
    public LiveData<Boolean> uncertainSubmit() {
        return uncertainSubmit;
    }

    // ------------------------------------------------------------- cart editing

    public void addDrug(@NonNull DrugResponse drug) {
        Cart updated = cart.getValue().add(drug);
        publish(updated, true);
    }

    public void setQuantity(long drugId, int quantity) {
        publish(cart.getValue().setQuantity(drugId, quantity), true);
    }

    public void setLinePrice(long drugId, @Nullable String priceText) {
        BigDecimal price = Money.parsePositive(priceText);
        publish(cart.getValue().setLinePrice(drugId, price), true);
    }

    public void removeLine(long drugId) {
        publish(cart.getValue().remove(drugId), true);
    }

    public void setDiscount(@Nullable String discountText) {
        BigDecimal discount = Money.parsePositive(discountText);
        publish(cart.getValue().withDiscount(discount == null ? BigDecimal.ZERO : discount), false);
    }

    public void setPaymentMethod(@NonNull PaymentMethod method) {
        Cart current = cart.getValue();
        // Leaving BANK_TRANSFER drops the destination, since the server rejects transfer
        // details on any other payment method.
        Cart updated = current.withTransfer(method,
                method == PaymentMethod.BANK_TRANSFER ? current.getTransfer() : null);
        // CREDIT is the only method that can leave debt, so it starts at zero paid.
        if (method == PaymentMethod.CREDIT) {
            amountPaid.setValue(BigDecimal.ZERO);
            publish(updated, false);
            return;
        }
        publish(updated, true);
    }

    /** Stores the bank transfer destination collected by the POS dialog. */
    public void setTransfer(@Nullable TransferDetails details) {
        Cart current = cart.getValue();
        publish(current.withTransfer(PaymentMethod.BANK_TRANSFER, details), true);
    }

    public void setCustomer(@Nullable CustomerResponse customer) {
        Cart updated = customer == null
                ? cart.getValue().withCustomer(null, null)
                : cart.getValue().withCustomer(customer.getId(), customer.getName());
        publish(updated, true);
    }

    public void clearCustomer() {
        setCustomer(null);
    }

    public void setAmountPaid(@Nullable String paidText) {
        BigDecimal paid = Money.parsePositive(paidText);
        amountPaid.setValue(paid == null ? BigDecimal.ZERO : paid);
        recompute();
    }

    public void resetCart() {
        publish(Cart.empty(), false);
        amountPaid.setValue(BigDecimal.ZERO);
        uncertainSubmit.setValue(false);
        recompute();
    }

    private void publish(@NonNull Cart updated, boolean keepAmountPaid) {
        cart.setValue(updated);
        if (!keepAmountPaid) {
            // Changing the discount or payment method invalidates a typed "amount paid",
            // so re-default it to the new total.
            defaultAmountPaidToTotal();
        } else {
            clampAmountPaidToTotal();
        }
        recompute();
    }

    /**
     * Keeps a prefilled paid amount valid after the total moves under it.
     *
     * <p>The paid field is prefilled with the total, so editing a line price can leave it
     * above the new total - which the server rejects outright. Following the total is what
     * a cashier means anyway: they are not deliberately overpaying.</p>
     */
    private void clampAmountPaidToTotal() {
        Cart current = cart.getValue();
        BigDecimal total = current.totals(BigDecimal.ZERO).getTotal();
        BigDecimal paid = amountPaid.getValue();
        if (paid != null && paid.compareTo(total) > 0) {
            amountPaid.setValue(total);
        }
    }

    /** Casher convenience: prefill the paid field with the exact total. */
    private void defaultAmountPaidToTotal() {
        Cart current = cart.getValue();
        BigDecimal total = current.totals(BigDecimal.ZERO).getTotal();
        amountPaid.setValue(current.getPaymentMethod() == PaymentMethod.CREDIT
                ? BigDecimal.ZERO
                : total);
    }

    private void recompute() {
        Cart current = cart.getValue();
        BigDecimal paid = amountPaid.getValue() == null ? BigDecimal.ZERO : amountPaid.getValue();
        summary.setValue(new Summary(current, current.totals(paid), current.validate(paid)));
    }

    // ------------------------------------------------------------------ search

    public void searchDrugs(@Nullable String text) {
        String query = text == null ? "" : text.trim();
        if (query.length() < 2) {
            searchResults.setValue(Collections.emptyList());
            return;
        }
        searching.setValue(true);
        disposables.add(drugRepository.byNameSearch(query)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        results -> {
                            searching.setValue(false);
                            searchResults.setValue(results);
                        },
                        throwable -> {
                            searching.setValue(false);
                            searchResults.setValue(Collections.emptyList());
                            message.setValue(FormError.of(
                                    NetworkCall.asApiError(throwable).getMessage()));
                        }));
    }

    /** Barcode scanner lookup: {@code GET /drugs/barcode/{code}}. */
    public void addByBarcode(@NonNull String barcode) {
        searching.setValue(true);
        disposables.add(drugRepository.byBarcode(barcode.trim())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        drug -> {
                            searching.setValue(false);
                            addDrug(drug);
                            message.setValue(FormError.of(R.string.pos_added_to_cart));
                        },
                        throwable -> {
                            searching.setValue(false);
                            ApiError error = NetworkCall.asApiError(throwable);
                            if (ApiErrorCodes.NOT_FOUND.equals(error.getCode())) {
                                message.setValue(FormError.of(R.string.pos_barcode_unknown));
                            } else {
                                message.setValue(FormError.of(error.getMessage()));
                            }
                        }));
    }

    public void searchCustomers(@Nullable String text) {
        String query = text == null ? "" : text.trim();
        if (query.length() < 2) {
            customerResults.setValue(Collections.emptyList());
            return;
        }
        disposables.add(customerRepository.search(query, 20)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        results -> customerResults.setValue(results),
                        throwable -> customerResults.setValue(Collections.emptyList())));
    }

    private static List<CustomerResponse> toList(@NonNull CustomerResponse[] array) {
        List<CustomerResponse> list = new ArrayList<>(array.length);
        Collections.addAll(list, array);
        return list;
    }

    // ------------------------------------------------------------------ submit

    /**
     * Creates the sale. Guarded against double submission and never retried; on a
     * transport failure the screen warns that the sale may exist and tells the cashier to
     * check the sales list first.
     */
    public void confirmSale() {
        if (Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        Summary current = summary.getValue();
        if (current == null || !current.canSubmit()) {
            return;
        }

        List<CreateSaleItemRequest> items = new ArrayList<>();
        for (CartLine line : current.getCart().lines()) {
            BigDecimal price = line.effectivePrice();
            if (price == null) {
                return;
            }
            // Only send an explicit price when the cashier overrode the drug default;
            // otherwise let the server use the drug's sellingPrice.
            items.add(line.hasOverride()
                    ? CreateSaleItemRequest.withPrice(line.getDrugId(), line.getQuantity(), price)
                    : CreateSaleItemRequest.withDefaultPrice(line.getDrugId(),
                    line.getQuantity()));
        }

        BigDecimal paid = amountPaid.getValue() == null ? BigDecimal.ZERO : amountPaid.getValue();
        Cart cartSnapshot = current.getCart();

        submitting.setValue(true);
        disposables.add(salesRepository.create(
                        cartSnapshot.getCustomerId(),
                        cartSnapshot.getPaymentMethod(),
                        items,
                        cartSnapshot.getDiscount(),
                        paid,
                        cartSnapshot.getTransfer())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        sale -> {
                            submitting.setValue(false);
                            uncertainSubmit.setValue(false);
                            completedSale.setValue(sale);
                            resetCart();
                        },
                        throwable -> {
                            submitting.setValue(false);
                            ApiError error = NetworkCall.asApiError(throwable);
                            if (error.isNetworkFailure()) {
                                // The request may have been processed before the
                                // connection dropped; never retry silently.
                                uncertainSubmit.setValue(true);
                                message.setValue(FormError.of(R.string.pos_submit_uncertain));
                                return;
                            }
                            message.setValue(FormError.of(error.getMessage()));
                        }));
    }

    @Override
    protected void onCleared() {
        disposables.clear();
    }
}