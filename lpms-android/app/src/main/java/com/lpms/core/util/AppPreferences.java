package com.lpms.core.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Plain preferences for <b>non-secret</b> local settings only: currency symbol,
 * language, theme and the online/offline mode. Credentials never go here — they live
 * in {@link com.lpms.core.auth.SessionStore} (EncryptedSharedPreferences).
 *
 * <p>The API endpoint is not a setting: it is fixed in {@code lpms.baseUrl}.</p>
 */
@Singleton
public final class AppPreferences {

    private static final String FILE = "lpms_prefs";

    private static final String K_CURRENCY = "currency_symbol";
    private static final String K_LANGUAGE = "language_tag";
    private static final String K_THEME = "theme_mode";
    private static final String K_MODE = "app_mode";

    /**
     * Israeli new shekel (NIS), U+20AA. Screens use this as their placeholder before the
     * stored preference is read, so the currency is defined in exactly one place.
     */
    public static final String CURRENCY_DEFAULT = "\u20AA";

    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    /** Talking to the configured backend; credentials and data both come from there. */
    public static final String MODE_ONLINE = "online";

    /**
     * Local only. Every screen reads and writes the on-device database, and no request
     * leaves the phone. A preview of the app rather than the pharmacy's real books.
     */
    public static final String MODE_OFFLINE = "offline";

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

    /** {@link #MODE_ONLINE} or {@link #MODE_OFFLINE}. Online is the default. */
    @NonNull
    public String appMode() {
        return prefs.getString(K_MODE, MODE_ONLINE);
    }

    public boolean isOffline() {
        return MODE_OFFLINE.equals(appMode());
    }

    /** Written with {@code commit()} so a mode change survives a process death. */
    public void setAppMode(@NonNull String mode) {
        String value = MODE_OFFLINE.equals(mode) ? MODE_OFFLINE : MODE_ONLINE;
        prefs.edit().putString(K_MODE, value).commit();
    }
}