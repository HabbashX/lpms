package com.lpms.core.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * All money display and input parsing. {@link BigDecimal} only — the class offers no
 * {@code double} overload on purpose.
 *
 * <p>Display is locale-aware (grouping separators and digits follow the device locale,
 * including Arabic-Indic digits), while everything sent to the backend is plain
 * {@code .}-decimal via {@link #toPlainString(BigDecimal)}.</p>
 */
public final class Money {

    /** Currencies here carry two decimals; keep it configurable if that changes. */
    private static final int DISPLAY_SCALE = 2;

    private Money() {
    }

    /**
     * @param symbol currency symbol from local settings (the backend has no currency field)
     * @return e.g. {@code ₪1,234.50} in English, {@code ١٬٢٣٤٫٥٠ ₪}-style grouping in Arabic
     */
    @NonNull
    public static String format(@Nullable BigDecimal value, @NonNull String symbol) {
        return format(value, symbol, Locale.getDefault());
    }

    @NonNull
    public static String format(@Nullable BigDecimal value,
                                @NonNull String symbol,
                                @NonNull Locale locale) {
        if (value == null) {
            return withSymbol("0.00", symbol, locale);
        }
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(locale));
        return withSymbol(format.format(value), symbol, locale);
    }

    /** No currency symbol; useful inside charts and dense tables. */
    @NonNull
    public static String plain(@Nullable BigDecimal value) {
        return plain(value, Locale.getDefault());
    }

    @NonNull
    public static String plain(@Nullable BigDecimal value, @NonNull Locale locale) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(locale));
        return format.format(value == null ? BigDecimal.ZERO : value);
    }

    /**
     * Percentage helper for margin / markup tiles. {@code fraction} is 0…1 (e.g. 0.2 for
     * 20%), rendered as {@code 20.0%}.
     */
    @NonNull
    public static String percent(@Nullable BigDecimal fraction, @NonNull Locale locale) {
        if (fraction == null) {
            return "—";
        }
        BigDecimal percent = fraction.multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
        DecimalFormat format = new DecimalFormat("#,##0.0", DecimalFormatSymbols.getInstance(locale));
        return format.format(percent) + "%";
    }

    /** Renders a percent value that the backend already scaled (20.0 → {@code 20.0%}). */
    @NonNull
    public static String percentValue(@Nullable BigDecimal percentValue, @NonNull Locale locale) {
        if (percentValue == null) {
            return "—";
        }
        DecimalFormat format = new DecimalFormat("#,##0.0", DecimalFormatSymbols.getInstance(locale));
        return format.format(percentValue) + "%";
    }

    /**
     * Parses user input into a BigDecimal. Accepts Arabic-Indic and Eastern Arabic-Indic
     * digits, both {@code ,} and {@code .} as the decimal mark, and stray spaces.
     *
     * @return null when the text is empty, malformed or negative — callers decide whether
     * that is an error or simply "leave unchanged"
     */
    @Nullable
    public static BigDecimal parse(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = normalizeDigits(raw).replace(" ", "").replace("\u00A0", "");
        if (normalized.isEmpty()) {
            return null;
        }
        // A single separator is a decimal mark; the last one wins ("1,234.50" style input
        // collapses to "1234.50").
        int lastComma = normalized.lastIndexOf(',');
        int lastDot = normalized.lastIndexOf('.');
        int decimalMark = Math.max(lastComma, lastDot);
        if (decimalMark >= 0) {
            String integerPart = normalized.substring(0, decimalMark).replace(",", "").replace(".", "");
            String fractionPart = normalized.substring(decimalMark + 1)
                    .replace(",", "").replace(".", "");
            normalized = integerPart + "." + fractionPart;
        } else {
            normalized = normalized.replace(",", "").replace(".", "");
        }
        if (normalized.isEmpty() || normalized.equals("-") || normalized.equals(".")) {
            return null;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Same as {@link #parse} but never negative — for quantities and non-negative amounts. */
    @Nullable
    public static BigDecimal parsePositive(@Nullable String raw) {
        BigDecimal value = parse(raw);
        return value == null || value.signum() < 0 ? null : value;
    }

    /** Maps Arabic-Indic (٠-٩) and Eastern Arabic-Indic (۰-۹) digits onto ASCII. */
    @NonNull
    private static String normalizeDigits(@NonNull String input) {
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= '\u0660' && c <= '\u0669') {
                out.append((char) ('0' + (c - '\u0660')));
            } else if (c >= '\u06F0' && c <= '\u06F9') {
                out.append((char) ('0' + (c - '\u06F0')));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Currency symbol before the amount for LTR locales, after it for RTL ones. */
    @NonNull
    private static String withSymbol(@NonNull String amount,
                                     @NonNull String symbol,
                                     @NonNull Locale locale) {
        String safeSymbol = symbol.trim().isEmpty() ? "" : symbol.trim() + " ";
        if (safeSymbol.isEmpty()) {
            return amount;
        }
        return isRtl(locale) ? amount + " " + safeSymbol.trim() : safeSymbol + amount;
    }

    public static boolean isRtl(@NonNull Locale locale) {
        String script = locale.getScript();
        if (script == null || script.isEmpty()) {
            // Locale.getScript() is empty on older releases; fall back to the display name.
            script = locale.getDisplayName(locale);
        }
        return "Arab".equalsIgnoreCase(script)
                || "Hebr".equalsIgnoreCase(script)
                || "Syrc".equalsIgnoreCase(script)
                || "Thaa".equalsIgnoreCase(script);
    }

    /** Wire format for JSON and query parameters: always {@code .} decimal, no grouping. */
    @NonNull
    public static String toPlainString(@Nullable BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    /** Scaled to 2 decimals for display in receipts; null-safe. */
    @NonNull
    public static BigDecimal scaled(@Nullable BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
    }
}