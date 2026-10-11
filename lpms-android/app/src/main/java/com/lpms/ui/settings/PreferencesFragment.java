package com.lpms.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;

import com.lpms.R;
import com.lpms.core.util.AppPreferences;
import com.lpms.databinding.FragmentPreferencesBinding;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Device preferences: language and theme.
 *
 * <p>Separate from the pharmacy settings screen, which edits server-side rules (expired
 * sales, negative balances) rather than anything about this handset. Both changes here take
 * effect immediately: the night mode and locale calls recreate the Activity stack.</p>
 */
@AndroidEntryPoint
public final class PreferencesFragment extends Fragment {

    /**
     * Language tags, matched to {@code res/values-ar} for the Arabic table.
     *
     * <p>System is stored as an empty tag, so "follow the device" is an ordinary stored
     * choice rather than a missing key that defaults somewhere surprising.</p>
     */
    private static final String LANG_SYSTEM = "";
    private static final String LANG_ENGLISH = "en";
    private static final String LANG_ARABIC = "ar";

    @Inject
    AppPreferences preferences;

    private FragmentPreferencesBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPreferencesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.toolbar.setNavigationOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        // Check the stored choice rather than trusting the widget, so the screen shows the
        // truth even if it is recreated after something else changed the setting.
        checkTheme(preferences.themeMode());
        checkLanguage(preferences.languageTag());

        binding.themeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String mode = modeFor(checkedId);
            preferences.setThemeMode(mode);
            applyNightMode(mode);
        });

        binding.languageGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String tag = languageFor(checkedId);
            preferences.setLanguageTag(tag);
            applyLocale(tag);
        });
    }

    private void checkTheme(@Nullable String mode) {
        int id;
        if (AppPreferences.THEME_LIGHT.equals(mode)) {
            id = R.id.theme_light;
        } else if (AppPreferences.THEME_DARK.equals(mode)) {
            id = R.id.theme_dark;
        } else {
            id = R.id.theme_system;
        }
        binding.themeGroup.check(id);
    }

    private void checkLanguage(@Nullable String tag) {
        String value = tag == null ? LANG_SYSTEM : tag.trim();
        int id;
        if (LANG_ARABIC.equalsIgnoreCase(value)) {
            id = R.id.language_arabic;
        } else if (LANG_ENGLISH.equalsIgnoreCase(value)) {
            id = R.id.language_english;
        } else {
            id = R.id.language_system;
        }
        binding.languageGroup.check(id);
    }

    @NonNull
    private static String modeFor(int checkedId) {
        if (checkedId == R.id.theme_light) {
            return AppPreferences.THEME_LIGHT;
        }
        if (checkedId == R.id.theme_dark) {
            return AppPreferences.THEME_DARK;
        }
        return AppPreferences.THEME_SYSTEM;
    }

    @NonNull
    private static String languageFor(int checkedId) {
        if (checkedId == R.id.language_english) {
            return LANG_ENGLISH;
        }
        if (checkedId == R.id.language_arabic) {
            return LANG_ARABIC;
        }
        return LANG_SYSTEM;
    }

    /** Theme.LPMS is DayNight, so only the night mode needs choosing. */
    private static void applyNightMode(@NonNull String mode) {
        if (AppPreferences.THEME_LIGHT.equals(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (AppPreferences.THEME_DARK.equals(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    private static void applyLocale(@NonNull String tag) {
        if (tag.isEmpty()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}