package com.lpms.data.session

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local UI preferences: theme and language.
 *
 * These are device-local settings that never leave the phone — the backend
 * has no notion of them, so they are stored in SharedPreferences and applied
 * immediately.
 */
@Singleton
class UiPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** `null` means "follow the system theme". */
    val darkTheme: Boolean?
        get() = if (prefs.contains(KEY_DARK_THEME)) {
            prefs.getBoolean(KEY_DARK_THEME, false)
        } else {
            null
        }

    fun setDarkTheme(value: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_THEME, value).apply()
    }

    private companion object {
        const val PREFS = "lpms_ui_prefs"
        const val KEY_DARK_THEME = "dark_theme"
    }
}
