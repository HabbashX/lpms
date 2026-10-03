package com.lpms.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val LpmsLightColors = lightColorScheme(
    primary = LpmsPrimary,
    onPrimary = LpmsOnPrimary,
    primaryContainer = LpmsPrimary,
    onPrimaryContainer = LpmsOnPrimary,
    secondary = LpmsPrimary,
    onSecondary = LpmsOnPrimary,
    secondaryContainer = LpmsSurfaceVariantLight,
    onSecondaryContainer = LpmsTextPrimaryLight,
    tertiary = LpmsPrimary,
    onTertiary = LpmsOnPrimary,
    background = LpmsBackgroundLight,
    onBackground = LpmsTextPrimaryLight,
    surface = LpmsSurfaceLight,
    onSurface = LpmsTextPrimaryLight,
    surfaceVariant = LpmsSurfaceVariantLight,
    onSurfaceVariant = LpmsTextSecondaryLight,
    surfaceTint = LpmsPrimary,
    outline = LpmsOutlineLight,
    outlineVariant = LpmsOutlineLight,
    error = LpmsDanger,
    onError = Color.White,
    errorContainer = LpmsDanger,
    onErrorContainer = Color.White,
)

private val LpmsDarkColors = darkColorScheme(
    primary = LpmsPrimary.lightness(0.55f),
    onPrimary = LpmsOnPrimary.lightness(0.10f),
    primaryContainer = LpmsPrimary.lightness(0.30f),
    onPrimaryContainer = LpmsOnPrimary.lightness(0.92f),
    secondary = LpmsPrimary.lightness(0.55f),
    onSecondary = LpmsOnPrimary.lightness(0.10f),
    secondaryContainer = LpmsTextPrimaryLight.lightness(0.18f),
    onSecondaryContainer = LpmsTextPrimaryLight.lightness(0.90f),
    tertiary = LpmsPrimary.lightness(0.55f),
    onTertiary = LpmsOnPrimary.lightness(0.10f),
    background = LpmsBackgroundLight.lightness(0.04f),
    onBackground = LpmsTextPrimaryLight.lightness(0.93f),
    surface = LpmsSurfaceLight.lightness(0.11f),
    onSurface = LpmsTextPrimaryLight.lightness(0.93f),
    surfaceVariant = LpmsSurfaceVariantLight.lightness(0.16f),
    onSurfaceVariant = LpmsTextSecondaryLight.lightness(0.65f),
    surfaceTint = LpmsPrimary.lightness(0.55f),
    outline = LpmsOutlineLight.lightness(0.32f),
    outlineVariant = LpmsSurfaceVariantLight.lightness(0.22f),
    error = LpmsDanger.lightness(0.64f),
    onError = LpmsOnPrimary.lightness(0.10f),
    errorContainer = LpmsDanger.lightness(0.30f),
    onErrorContainer = LpmsOnPrimary.lightness(0.92f),
)

private val LpmsLightSemantic = LpmsSemanticColors(
    success = LpmsSuccess,
    warning = LpmsWarning,
    debt = LpmsDebt,
    danger = LpmsDanger,
)

private val LpmsDarkSemantic = LpmsSemanticColors(
    success = LpmsSuccess.lightness(0.58f),
    warning = LpmsWarning.lightness(0.60f),
    debt = LpmsDebt.lightness(0.60f),
    danger = LpmsDanger.lightness(0.65f),
)

/**
 * Single source of truth for the app's look.
 *
 * Colours come from the spec §7 palette; dark mode is the same palette with
 * HSL lightness derived, not a hand-picked second set.
 */
@Composable
fun LpmsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) LpmsDarkColors else LpmsLightColors
    val semanticColors = if (darkTheme) LpmsDarkSemantic else LpmsLightSemantic

    CompositionLocalProvider(LocalLpmsSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LpmsTypography,
            content = content,
        )
    }
}

/** Convenience accessor for the semantic colours. */
object LpmsColors {
    val semantic: LpmsSemanticColors
        @Composable get() = LocalLpmsSemanticColors.current
}
