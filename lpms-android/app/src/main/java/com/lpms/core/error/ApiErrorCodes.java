package com.lpms.core.error;

/**
 * The closed set of {@code code} values the backend can return. Everything else
 * must be handled as {@link #UNKNOWN}; no message-text matching anywhere.
 *
 * <p>Values are copied verbatim from the backend contract.</p>
 */
public final class ApiErrorCodes {

    private ApiErrorCodes() {
    }

    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String MALFORMED_REQUEST = "MALFORMED_REQUEST";
    public static final String INVALID_PARAMETER = "INVALID_PARAMETER";
    public static final String MISSING_PARAMETER = "MISSING_PARAMETER";
    public static final String INVALID_ARGUMENT = "INVALID_ARGUMENT";

    public static final String WEAK_PASSWORD = "WEAK_PASSWORD";
    public static final String INVALID_CURRENT_PASSWORD = "INVALID_CURRENT_PASSWORD";
    public static final String PASSWORD_REUSE = "PASSWORD_REUSE";

    public static final String INVALID_SALE = "INVALID_SALE";
    public static final String INVALID_PRICING = "INVALID_PRICING";
    public static final String INVALID_MARGIN = "INVALID_MARGIN";
    public static final String INVALID_DATE_RANGE = "INVALID_DATE_RANGE";
    public static final String INVALID_PERIOD = "INVALID_PERIOD";
    public static final String INVALID_MONTH = "INVALID_MONTH";
    public static final String EXPIRATION_IN_PAST = "EXPIRATION_IN_PAST";
    public static final String INVALID_AMOUNT = "INVALID_AMOUNT";
    public static final String EMPTY_SETTINGS = "EMPTY_SETTINGS";
    public static final String UNKNOWN_SETTING = "UNKNOWN_SETTING";
    public static final String INVALID_SETTING = "INVALID_SETTING";

    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";
    public static final String INVALID_REFRESH_TOKEN = "INVALID_REFRESH_TOKEN";
    public static final String INVALID_TOKEN = "INVALID_TOKEN";
    public static final String TOKEN_REVOKED = "TOKEN_REVOKED";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";

    public static final String PASSWORD_CHANGE_REQUIRED = "PASSWORD_CHANGE_REQUIRED";
    public static final String FORBIDDEN = "FORBIDDEN";

    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";

    // 409
    public static final String INSUFFICIENT_STOCK = "INSUFFICIENT_STOCK";
    public static final String EXPIRED_STOCK = "EXPIRED_STOCK";
    public static final String CUSTOMER_INACTIVE = "CUSTOMER_INACTIVE";
    public static final String DRUG_INACTIVE = "DRUG_INACTIVE";
    public static final String BARCODE_ALREADY_EXISTS = "BARCODE_ALREADY_EXISTS";
    public static final String CATEGORY_IN_USE = "CATEGORY_IN_USE";
    public static final String CATEGORY_ALREADY_EXISTS = "CATEGORY_ALREADY_EXISTS";
    public static final String USERNAME_ALREADY_EXISTS = "USERNAME_ALREADY_EXISTS";
    public static final String LAST_ADMIN = "LAST_ADMIN";
    public static final String SELF_MODIFICATION_FORBIDDEN = "SELF_MODIFICATION_FORBIDDEN";
    public static final String CONFLICT = "CONFLICT";
    public static final String SALE_ALREADY_REFUNDED = "SALE_ALREADY_REFUNDED";
    public static final String SALE_ITEM_NOT_FOUND = "SALE_ITEM_NOT_FOUND";
    public static final String REFUND_QUANTITY_EXCEEDED = "REFUND_QUANTITY_EXCEEDED";
    public static final String NOTHING_TO_REFUND = "NOTHING_TO_REFUND";
    public static final String PAYMENT_EXCEEDS_DEBT = "PAYMENT_EXCEEDS_DEBT";
    public static final String NEGATIVE_BALANCE = "NEGATIVE_BALANCE";
    public static final String ACCOUNT_MISSING = "ACCOUNT_MISSING";

    // 423 / 429 / 500
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    /** Not produced by the backend; used when the envelope itself is unparseable. */
    public static final String UNKNOWN = "UNKNOWN";

    /** Client-side transport failures (no HTTP status at all). */
    public static final String NETWORK_UNAVAILABLE = "NETWORK_UNAVAILABLE";
    public static final String TIMEOUT = "TIMEOUT";

    /** True for the codes that must trigger exactly one silent refresh. */
    public static boolean isRefreshTrigger(String code) {
        return INVALID_TOKEN.equals(code)
                || TOKEN_REVOKED.equals(code)
                || UNAUTHORIZED.equals(code);
    }

    /** True when a refresh failure means the session is unrecoverable. */
    public static boolean isRefreshFailure(String code) {
        return INVALID_REFRESH_TOKEN.equals(code);
    }
}