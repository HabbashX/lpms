package com.lpms.core.network;

import androidx.annotation.NonNull;

import com.lpms.BuildConfig;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Applies the dev-only server URL override at request time.
 *
 * <p>Retrofit is built once against the flavour's {@code BuildConfig.BASE_URL}; this
 * interceptor rewrites scheme/host/port/path-prefix when the user has overridden
 * the server, so changing hosts never requires rebuilding the Retrofit instance.</p>
 *
 * <p>A no-op in release builds.</p>
 */
@Singleton
public final class BaseUrlInterceptor implements Interceptor {

    private final ServerUrl serverUrl;

    @Inject
    public BaseUrlInterceptor(@NonNull ServerUrl serverUrl) {
        this.serverUrl = serverUrl;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        if (!BuildConfig.ALLOW_SERVER_URL_OVERRIDE || !serverUrl.hasOverride()) {
            return chain.proceed(request);
        }

        HttpUrl target = HttpUrl.parse(serverUrl.baseUrl());
        if (target == null) {
            return chain.proceed(request);
        }

        HttpUrl original = request.url();
        String prefix = target.encodedPath();
        if (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }

        // Everything after the compile-time base URL is the API path; keep it intact.
        String compileTimeBase = HttpUrl.parse(BuildConfig.BASE_URL) == null
                ? ""
                : stripTrailingSlash(HttpUrl.parse(BuildConfig.BASE_URL).encodedPath());
        String apiPath = original.encodedPath();
        if (!compileTimeBase.isEmpty() && apiPath.startsWith(compileTimeBase)) {
            apiPath = apiPath.substring(compileTimeBase.length());
        }

        HttpUrl rewritten = target.newBuilder()
                .encodedPath(prefix + apiPath)
                .build();

        return chain.proceed(request.newBuilder().url(rewritten).build());
    }

    @NonNull
    private static String stripTrailingSlash(@NonNull String path) {
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}