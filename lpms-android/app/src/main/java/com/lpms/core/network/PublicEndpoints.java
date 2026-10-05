package com.lpms.core.network;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import okhttp3.Request;

/**
 * The only three calls that travel without an {@code Authorization} header:
 * {@code POST /api/v1/auth/login}, {@code POST /api/v1/auth/refresh} and the
 * public health probe {@code GET /}.
 *
 * <p>Everything else — including every 401/403 produced by the security filter —
 * is authenticated.</p>
 */
public final class PublicEndpoints {

    private static final List<String> LOGIN = Collections.singletonList("/api/v1/auth/login");
    private static final List<String> REFRESH = Collections.singletonList("/api/v1/auth/refresh");

    private PublicEndpoints() {
    }

    public static boolean isPublic(@NonNull Request request) {
        String path = request.url().encodedPath();

        // Health probe lives at the server root, outside the /api/v1 prefix.
        if ("GET".equals(request.method())
                && (path.equals("/") || path.isEmpty() || path.equals("/health"))) {
            return true;
        }
        if (!"POST".equals(request.method())) {
            return false;
        }
        return matches(path, LOGIN) || matches(path, REFRESH);
    }

    public static boolean isLogin(@NonNull Request request) {
        return "POST".equals(request.method())
                && matches(request.url().encodedPath(), LOGIN);
    }

    public static boolean isRefresh(@NonNull Request request) {
        return "POST".equals(request.method())
                && matches(request.url().encodedPath(), REFRESH);
    }

    private static boolean matches(@NonNull String actualPath, @NonNull List<String> paths) {
        return paths.stream().anyMatch(p -> samePath(actualPath, p));
    }

    /**
     * Compares paths ignoring a trailing slash so {@code /auth/login} and
     * {@code /auth/login/} are treated as the same endpoint. The dev server URL may
     * be configured with or without a trailing slash.
     */
    private static boolean samePath(@NonNull String actual, @NonNull String expected) {
        String a = stripTrailingSlash(actual);
        String e = stripTrailingSlash(expected);
        if (a.equals(e)) {
            return true;
        }
        // Defensive: tolerate a base URL configured with a path prefix.
        return a.endsWith(e) && a.length() > e.length()
                && a.charAt(a.length() - e.length() - 1) == '/';
    }

    private static String stripTrailingSlash(@NonNull String path) {
        int end = path.length();
        while (end > 1 && path.charAt(end - 1) == '/') {
            end--;
        }
        return path.substring(0, end);
    }

    /** Exposed for tests. */
    public static List<String> publicPaths() {
        return Arrays.asList("/api/v1/auth/login", "/api/v1/auth/refresh", "/");
    }
}