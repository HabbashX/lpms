package com.lpms.ui.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Per-app language switching.
 *
 * Android 13+ also exposes this in system settings (declared in
 * `res/xml/locales_config.xml`); below that, [set] is the only way in.
 * AppCompat persists the choice across restarts thanks to
 * `autoStoreLocales` on `AppLocalesMetadataHolderService`.
 */
object AppLocale {

    const val ENGLISH = "en"
    const val ARABIC = "ar"

    /** BCP-47 tag of the language the app is currently displayed in. */
    fun currentTag(): String {
        val applied = AppCompatDelegate.getApplicationLocales()
        if (!applied.isEmpty) return applied.get(0)?.language ?: ENGLISH
        return java.util.Locale.getDefault().language
    }

    fun isRtl(): Boolean = currentTag() == ARABIC

    /** Switches the whole app to [tag]; recreates the running activity. */
    fun set(tag: String) {
        if (tag == currentTag() && !AppCompatDelegate.getApplicationLocales().isEmpty) return
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    /** Clears any app-level override so the system locale wins again. */
    fun clearOverride() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }
}
