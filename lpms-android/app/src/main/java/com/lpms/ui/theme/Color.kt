package com.lpms.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * LPMS palette — spec §7.
 *
 * Light surface colours are the literal spec tokens. Dark-mode surfaces are
 * derived from them with HSL lightness changes (see [lightness]), never by
 * eye-dropping a screenshot.
 */

// ── Brand ────────────────────────────────────────────────────────────────────
val LpmsPrimary = Color(0xFF0F766E)
val LpmsOnPrimary = Color(0xFFFFFFFF)
val LpmsPrimaryContainer = Color(0xFFCCFBF1)
val LpmsOnPrimaryContainer = Color(0xFF134E4A)

// ── Neutral (stone) ─────────────────────────────────────────────────────────
val LpmsBackgroundLight = Color(0xFFFAFAF9)
val LpmsSurfaceLight = Color(0xFFFFFFFF)
val LpmsSurfaceVariantLight = Color(0xFFF5F5F4)
val LpmsOutlineLight = Color(0xFFE7E5E4)
val LpmsTextPrimaryLight = Color(0xFF1C1917)
val LpmsTextSecondaryLight = Color(0xFF78716C)

// ── Status ──────────────────────────────────────────────────────────────────
val LpmsSuccess = Color(0xFF15803D)
val LpmsWarning = Color(0xFFB45309)
val LpmsDebt = Color(0xFFB45309)
val LpmsDanger = Color(0xFFB91C1C)

/**
 * Semantic colours that Compose's [androidx.compose.material3.ColorScheme] has
 * no slot for. Kept in a CompositionLocal so a single [LpmsTheme] switch flips
 * them with light/dark.
 */
data class LpmsSemanticColors(
    val success: Color,
    val warning: Color,
    val debt: Color,
    val danger: Color,
)

val LocalLpmsSemanticColors = staticCompositionLocalOf {
    LpmsSemanticColors(
        success = LpmsSuccess,
        warning = LpmsWarning,
        debt = LpmsDebt,
        danger = LpmsDanger,
    )
}

// ── HSL derivation helpers ──────────────────────────────────────────────────

private fun Color.toHsl(): FloatArray {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val lightness = (max + min) / 2f
    var hue = 0f
    var saturation = 0f
    if (max != min) {
        val delta = max - min
        saturation = if (lightness > 0.5f) delta / (2f - max - min) else delta / (max + min)
        hue = (when (max) {
            r -> (g - b) / delta + (if (g < b) 6f else 0f)
            g -> (b - r) / delta + 2f
            else -> (r - g) / delta + 4f
        } / 6f)
    }
    return floatArrayOf(hue, saturation, lightness)
}

private fun fromHsl(hue: Float, saturation: Float, lightness: Float): Color {
    if (saturation == 0f) return Color(lightness, lightness, lightness)
    val q = if (lightness < 0.5f) lightness * (1f + saturation) else lightness + saturation - lightness * saturation
    val p = 2f * lightness - q
    fun channel(offset: Float): Float {
        var t = hue + offset
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        if (t < 1f / 6f) return p + (q - p) * 6f * t
        if (t < 1f / 2f) return q
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6f
        return p
    }
    return Color(channel(1f / 3f), channel(0f), channel(-1f / 3f))
}

/** Returns this colour with its HSL lightness replaced, hue and saturation kept. */
internal fun Color.lightness(value: Float): Color {
    val (hue, saturation, _) = toHsl()
    return fromHsl(hue, saturation, value.coerceIn(0f, 1f))
}
