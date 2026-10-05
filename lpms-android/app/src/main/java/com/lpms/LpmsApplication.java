package com.lpms;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.lpms.core.auth.SessionManager;
import com.lpms.core.util.AppPreferences;

import javax.inject.Inject;

import dagger.hilt.android.HiltAndroidApp;

/**
 * Application entry point. {@code @HiltAndroidApp} generates the singleton
 * component that all {@code @AndroidEntryPoint} classes attach to.
 *
 * <p>Startup order matters: theme first (avoids a flash of the wrong mode), then
 * the encrypted session is restored into {@link SessionManager} so the session
 * gate can decide Login vs Home without an extra disk hop.</p>
 */
@HiltAndroidApp
public class LpmsApplication extends Application {

    @Inject
    public SessionManager sessionManager;

    @Inject
    public AppPreferences preferences;

    @Override
    public void onCreate() {
        super.onCreate();
        applyTheme(preferences.themeMode());
        sessionManager.restore();
    }

    private static void applyTheme(String mode) {
        if (AppPreferences.THEME_LIGHT.equals(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (AppPreferences.THEME_DARK.equals(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }
}