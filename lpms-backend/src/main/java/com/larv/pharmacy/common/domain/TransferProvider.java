package com.larv.pharmacy.common.domain;

/**
 * Where a {@link PaymentMethod#BANK_TRANSFER} payment is actually sent.
 *
 * <p>The three options are not variations of one thing, which is why the
 * identifier collected alongside them differs by provider:</p>
 * <ul>
 *   <li>{@link #PAYPAL} - a PayPal account, identified by email. There is no phone
 *       number and no "account name" in the bank sense.</li>
 *   <li>{@link #BANK_OF_PALESTINE} - a real bank account, identified by account number.</li>
 *   <li>{@link #JAWWAL_PAY} - a Jawwal mobile wallet, identified by phone number.</li>
 * </ul>
 *
 * <p>Stored on {@code sales.transfer_provider}; the constraint lives in
 * {@code V11__add_bank_transfer_details.sql} and must be kept in step with this enum.</p>
 */
public enum TransferProvider {

    PAYPAL("PayPal"),
    BANK_OF_PALESTINE("Bank of Palestine"),
    JAWWAL_PAY("Jawwal Pay");

    private final String displayName;

    TransferProvider(String displayName) {
        this.displayName = displayName;
    }

    /** Human-readable name for receipts and audit trails. */
    public String displayName() {
        return displayName;
    }

    /** What the account identifier actually is, so the UI can label it correctly. */
    public String identifierLabel() {
        switch (this) {
            case PAYPAL:
                return "PayPal email";
            case BANK_OF_PALESTINE:
                return "Account number";
            case JAWWAL_PAY:
            default:
                return "Phone number";
        }
    }

    /** Placeholder text matching {@link #identifierLabel()}. */
    public String identifierHint() {
        switch (this) {
            case PAYPAL:
                return "name@example.com";
            case BANK_OF_PALESTINE:
                return "e.g. 123456789";
            case JAWWAL_PAY:
            default:
                return "e.g. 0591234567";
        }
    }
}
