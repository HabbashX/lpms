package com.lpms.formatting

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Number and money formatting.
 *
 * Arabic UI still wants Western digits for POS readability (prices are typed
 * and compared on screen), so Arabic gets `nu-latn` Unicode keywords while
 * keeping the Arabic locale for currency/word order.
 */
object LpmsNumberLocale {

    /**
     * [locale] with its numeral system forced to Latin digits.
     * Returns [locale] unchanged if the keyword cannot be applied.
     */
    fun withLatinDigits(locale: Locale): Locale = runCatching {
        Locale.Builder()
            .setLocale(locale)
            .setUnicodeLocaleKeyword("nu", "latn")
            .build()
    }.getOrDefault(locale)

    /** The locale used for all numeric output in the app. */
    fun numeric(locale: Locale): Locale =
        if (locale.language == "ar") withLatinDigits(locale) else locale
}

/**
 * Formats money using [currencyCode] (a non-translatable string resource so a
 * wrong currency is a one-line fix, never a code change).
 */
class MoneyFormatter(private val currencyCode: String) {

    fun format(amount: BigDecimal?, locale: Locale): String {
        if (amount == null) return "—"
        val format = NumberFormat.getCurrencyInstance(LpmsNumberLocale.numeric(locale))
        runCatching { Currency.getInstance(currencyCode) }
            .getOrNull()
            ?.let { format.currency = it }
        return format.format(amount)
    }

    fun format(amount: Double?, locale: Locale): String =
        format(amount?.let { BigDecimal.valueOf(it) }, locale)

    /** Plain integer with locale grouping (Arabic still shows Western digits). */
    fun formatCount(value: Number, locale: Locale): String =
        NumberFormat.getIntegerInstance(LpmsNumberLocale.numeric(locale)).format(value)

    /** Short date, e.g. `14/05/2026`. */
    fun formatDate(epochMillis: Long, locale: Locale): String =
        java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT, locale)
            .format(java.util.Date(epochMillis))
}
