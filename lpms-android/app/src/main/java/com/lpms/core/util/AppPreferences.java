package com.lpms.core.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Plain preferences for <b>non-secret</b> local settings only: currency symbol,
 * language and theme. Credentials never go here — they live in
 * {@link com.lpms.core.auth.SessionStore} (EncryptedSharedPreferences).
 *
 * <p>The API endpoint is not a setting: it is fixed in {@code lpms.baseUrl}.</p>
 */
@Singleton
public final class AppPreferences {

    private static final String FILE = "lpms_prefs";

    private static final String K_CURRENCY = "currency_symbol";
    private static final String K_LANGUAGE = "language_tag";
    private static final String K_THEME = "theme_mode";

    /**
     * Israeli new shekel (NIS), U+20AA. Screens use this as their placeholder before the
     * stored preference is read, so the currency is defined in exactly one place.
     */
    public static final String CURRENCY_DEFAULT = "\u20AA";

    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    private final SharedPreferences prefs;

    @Inject
    public AppPreferences(@NonNull Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    @NonNull
    public String currencySymbol() {
        return prefs.getString(K_CURRENCY, CURRENCY_DEFAULT);
    }

    public void setCurrencySymbol(@NonNull String symbol) {
        String trimmed = symbol.trim();
        prefs.edit().putString(K_CURRENCY, trimmed.isEmpty() ? CURRENCY_DEFAULT : trimmed).apply();
    }

    /** {@code null} follows the system locale. */
    public String languageTag() {
        return prefs.getString(K_LANGUAGE, null);
    }

    public void setLanguageTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            prefs.edit().remove(K_LANGUAGE).apply();
        } else {
            prefs.edit().putString(K_LANGUAGE, tag).apply();
        }
    }

    @NonNull
    public String themeMode() {
        return prefs.getString(K_THEME, THEME_SYSTEM);
    }

    public void setThemeMode(@NonNull String mode) {
        prefs.edit().putString(K_THEME, mode).apply();
    }
}