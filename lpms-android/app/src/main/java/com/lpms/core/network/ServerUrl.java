package com.lpms.core.network;

import androidx.annotation.NonNull;

import com.lpms.BuildConfig;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Single source of truth for "where does the API live".
 *
 * <p>The base URL is the deployed Railway endpoint from {@code lpms.baseUrl} in
 * {@code gradle.properties}, surfaced as {@code BuildConfig.BASE_URL}. There is no
 * development variant and no runtime host override any more — one host, HTTPS only,
 * one place to change it.</p>
 *
 * <p>Path layout: {@code <base>} + {@code /api/v1/} + {@code <relative>}.</p>
 */
@Singleton
public final class ServerUrl {

    private final String baseUrl;

    @Inject
    public ServerUrl() {
        this.baseUrl = withTrailingSlash(BuildConfig.BASE_URL);
    }

    /** Effective base URL, always ending with a single {@code /}. */
    @NonNull
    public String baseUrl() {
        return baseUrl;
    }

    /** Absolute URL for an API path given without the {@code /api/v1} prefix. */
    @NonNull
    public String apiUrl(@NonNull String relativePath) {
        String prefix = BuildConfig.API_PREFIX;
        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        String relative = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
        return baseUrl + prefix + relative;
    }

    /** Public health probe lives at the server root: {@code GET /}. */
    @NonNull
    public String healthUrl() {
        return baseUrl;
    }

    @NonNull
    private static String withTrailingSlash(@NonNull String url) {
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed : trimmed + "/";
    }
}