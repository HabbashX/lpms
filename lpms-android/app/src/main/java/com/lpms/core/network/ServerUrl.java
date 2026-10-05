package com.lpms.core.network;

import androidx.annotation.NonNull;

import com.lpms.BuildConfig;
import com.lpms.core.util.AppPreferences;

import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Single source of truth for "where does the API live".
 *
 * <p>Base URL comes from the build flavour ({@code gradle.properties} →
 * {@code buildConfigField}). On a dev build the user may point the app at another
 * host from the login screen; that override is stored in plain preferences (not a
 * secret) and applied by {@link BaseUrlInterceptor} so Retrofit itself never has to
 * be rebuilt.</p>
 *
 * <p>Path layout: {@code <base>} + {@code /api/v1/} + {@code <relative>}.</p>
 */
@Singleton
public final class ServerUrl {

    private static final AtomicReference<String> OVERRIDE = new AtomicReference<>("");

    private final AppPreferences preferences;

    @Inject
    public ServerUrl(@NonNull AppPreferences preferences) {
        this.preferences = preferences;
        OVERRIDE.set(preferences.serverUrlOverride());
    }

    /** Effective base URL, always ending with a single {@code /}. */
    @NonNull
    public String baseUrl() {
        String override = OVERRIDE.get();
        if (BuildConfig.ALLOW_SERVER_URL_OVERRIDE && !override.isEmpty()) {
            return withTrailingSlash(override);
        }
        return withTrailingSlash(BuildConfig.BASE_URL);
    }

    /** Absolute URL for an API path given without the {@code /api/v1} prefix. */
    @NonNull
    public String apiUrl(@NonNull String relativePath) {
        String prefix = BuildConfig.API_PREFIX;
        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        String relative = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
        return baseUrl() + prefix + relative;
    }

    /** Public health probe lives at the server root: {@code GET /}. */
    @NonNull
    public String healthUrl() {
        return baseUrl();
    }

    public boolean hasOverride() {
        return BuildConfig.ALLOW_SERVER_URL_OVERRIDE && !OVERRIDE.get().isEmpty();
    }

    /** @return true when the value was accepted; false when it is not a usable URL. */
    public boolean setOverride(String candidate) {
        if (!BuildConfig.ALLOW_SERVER_URL_OVERRIDE) {
            return false;
        }
        String normalized = normalize(candidate);
        if (normalized == null) {
            return false;
        }
        OVERRIDE.set(normalized);
        preferences.setServerUrlOverride(normalized);
        return true;
    }

    public void clearOverride() {
        OVERRIDE.set("");
        preferences.clearServerUrlOverride();
    }

    /** Adds the scheme when the user typed {@code 10.0.2.2:8080} alone. */
    @NonNull
    public static String normalize(String candidate) {
        if (candidate == null) {
            return "";
        }
        String value = candidate.trim();
        if (value.isEmpty()) {
            return "";
        }
        if (!value.contains("://")) {
            value = "http://" + value;
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            return "";
        }
        // Must have a host.
        int schemeEnd = value.indexOf("://") + 3;
        if (schemeEnd >= value.length()) {
            return "";
        }
        return withTrailingSlash(value);
    }

    @NonNull
    private static String withTrailingSlash(@NonNull String url) {
        return url.endsWith("/") ? url : url + "/";
    }
}