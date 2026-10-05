package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * {@code GET /customers/{id}/account?transactionPage=0&transactionSize=20}.
 *
 * <p>Note the pagination parameters are named {@code transactionPage} /
 * {@code transactionSize}, not {@code page} / {@code size} as everywhere else.</p>
 */
public final class CustomerAccountResponse {

    @SerializedName("customer")
    private final CustomerResponse customer;

    @SerializedName("totalPurchases")
    private final java.math.BigDecimal totalPurchases;

    @SerializedName("totalPaid")
    private final java.math.BigDecimal totalPaid;

    @SerializedName("totalRefunds")
    private final java.math.BigDecimal totalRefunds;

    /** Outstanding debt. Show prominently; drives the "pay full debt" button. */
    @SerializedName("currentDebt")
    private final java.math.BigDecimal currentDebt;

    @SerializedName("transactions")
    private final PageResponse<TransactionResponse> transactions;

    public CustomerAccountResponse(@Nullable CustomerResponse customer,
                                   @Nullable java.math.BigDecimal totalPurchases,
                                   @Nullable java.math.BigDecimal totalPaid,
                                   @Nullable java.math.BigDecimal totalRefunds,
                                   @Nullable java.math.BigDecimal currentDebt,
                                   @Nullable PageResponse<TransactionResponse> transactions) {
        this.customer = customer;
        this.totalPurchases = totalPurchases;
        this.totalPaid = totalPaid;
        this.totalRefunds = totalRefunds;
        this.currentDebt = currentDebt;
        this.transactions = transactions;
    }

    @Nullable
    public CustomerResponse getCustomer() {
        return customer;
    }

    @Nullable
    public java.math.BigDecimal getTotalPurchases() {
        return totalPurchases;
    }

    @Nullable
    public java.math.BigDecimal getTotalPaid() {
        return totalPaid;
    }

    @Nullable
    public java.math.BigDecimal getTotalRefunds() {
        return totalRefunds;
    }

    @Nullable
    public java.math.BigDecimal getCurrentDebt() {
        return currentDebt;
    }

    /** May be null if the account has no ledger entries at all. */
    @Nullable
    public PageResponse<TransactionResponse> getTransactions() {
        return transactions;
    }

    /** Never-null view of the ledger rows for list adapters. */
    @NonNull
    public java.util.List<TransactionResponse> transactionRows() {
        return transactions == null ? java.util.Collections.emptyList() : transactions.getContent();
    }

    public boolean hasDebt() {
        return getCurrentDebt() != null && getCurrentDebt().signum() > 0;
    }
}