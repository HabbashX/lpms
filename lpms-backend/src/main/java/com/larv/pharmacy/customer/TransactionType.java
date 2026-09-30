package com.larv.pharmacy.customer;

/**
 * Kind of ledger entry. Every financial event of a customer account appends a
 * new immutable row; balances are never overwritten without a ledger entry.
 */
public enum TransactionType {
    /** A sale (increases debt when not fully paid). */
    SALE,
    /** A debt payment or an amount paid at the sale counter. */
    PAYMENT,
    /** A manual correction written by an administrator. */
    ADJUSTMENT,
    /** A refund that reduces what the customer owes. */
    REFUND
}
