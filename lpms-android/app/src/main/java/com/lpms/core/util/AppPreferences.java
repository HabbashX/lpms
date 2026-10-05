package com.lpms.core.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Plain preferences for <b>non-secret</b> local settings only: currency symbol,
 * language, theme, and the dev-only server URL override. Credentials never go here
 * — they live in {@link com.lpms.core.auth.SessionStore} (EncryptedSharedPreferences).
 */
@Singleton
public final class AppPreferences {

    private static final String FILE = "lpms_prefs";

    private static final String K_CURRENCY = "currency_symbol";
    private static final String K_LANGUAGE = "language_tag";
    private static final String K_THEME = "theme_mode";
    private static final String K_SERVER_URL = "server_url_override";

    private static final String DEFAULT_CURRENCY = "$";

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
        return prefs.getString(K_CURRENCY, DEFAULT_CURRENCY);
    }

    public void setCurrencySymbol(@NonNull String symbol) {
        String trimmed = symbol.trim();
        prefs.edit().putString(K_CURRENCY, trimmed.isEmpty() ? DEFAULT_CURRENCY : trimmed).apply();
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

    /** Dev flavor only; empty when unset, in which case BuildConfig.BASE_URL is used. */
    @NonNull
    public String serverUrlOverride() {
        return prefs.getString(K_SERVER_URL, "");
    }

    public void setServerUrlOverride(@NonNull String url) {
        prefs.edit().putString(K_SERVER_URL, url.trim()).apply();
    }

    public void clearServerUrlOverride() {
        prefs.edit().remove(K_SERVER_URL).apply();
    }
}