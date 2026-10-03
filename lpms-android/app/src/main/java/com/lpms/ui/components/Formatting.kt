package com.lpms.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import com.lpms.formatting.MoneyFormatter
import java.math.BigDecimal
import java.util.Locale

/**
 * Money formatting, provided once at the root from the `currency_code`
 * resource so no screen hardcodes a currency.
 */
val LocalMoney = staticCompositionLocalOf { MoneyFormatter("ILS") }

/** The locale the app is currently rendering in (honours the per-app switch). */
@Composable
fun currentLocale(): Locale =
    LocalConfiguration.current.locales.get(0) ?: Locale.getDefault()

@Composable
fun money(value: BigDecimal?): String =
    LocalMoney.current.format(value, currentLocale())

@Composable
fun money(value: Double?): String =
    LocalMoney.current.format(value, currentLocale())

/** Integer with locale grouping; Arabic still renders Western digits. */
@Composable
fun count(value: Number): String =
    LocalMoney.current.formatCount(value, currentLocale())

@Composable
fun shortDate(epochMillis: Long): String =
    LocalMoney.current.formatDate(epochMillis, currentLocale())
