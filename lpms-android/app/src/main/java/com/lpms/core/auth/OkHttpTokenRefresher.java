package com.lpms.core.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.lpms.core.error.ApiError;
import com.lpms.core.error.ApiErrorCodes;
import com.lpms.core.error.ApiErrorMapper;
import com.lpms.core.network.HttpJson;
import com.lpms.core.network.ServerUrl;
import com.lpms.data.dto.AuthResponse;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Blocking implementation of {@link TokenRefresher} used by the OkHttp
 * authenticator.
 *
 * <p>Uses its own bare {@link OkHttpClient}: no auth interceptor, no authenticator,
 * no logging. That guarantees the refresh request is sent exactly once and can
 * never recursively trigger another refresh.</p>
 *
 * <p><b>Critical invariant:</b> on success the new access token <em>and</em> the new
 * refresh token are returned together, and the caller persists them before retrying
 * the original request. The previous refresh token is already dead at that point.</p>
 */
@Singleton
public final class OkHttpTokenRefresher implements TokenRefresher {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final long DEFAULT_ACCESS_TTL_SECONDS = 3600L;
    private static final long DEFAULT_REFRESH_TTL_SECONDS = 14L * 24 * 60 * 60;

    private final OkHttpClient client;
    private final Gson gson;
    private final ApiErrorMapper errorMapper;
    private final ServerUrl serverUrl;

    @Inject
    public OkHttpTokenRefresher(@NonNull Gson gson,
                                @NonNull ServerUrl serverUrl,
                                @NonNull ApiErrorMapper errorMapper) {
        this.gson = gson;
        this.serverUrl = serverUrl;
        this.errorMapper = errorMapper;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build();
    }

    @NonNull
    @Override
    public Outcome refresh(@NonNull String refreshToken) {
        Request request = new Request.Builder()
                .url(serverUrl.apiUrl("auth/refresh"))
                .post(RequestBody.create("{\"refreshToken\":\"" + escapeJson(refreshToken) + "\"}", JSON))
                .header("Accept", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return Outcome.failure(mapFailure(response));
            }
            ResponseBody body = response.body();
            if (body == null) {
                return Outcome.failure(ApiError.unparseable(response.code(), null));
            }
            AuthResponse parsed = gson.fromJson(body.string(), AuthResponse.class);
            if (parsed == null || isBlank(parsed.getAccessToken()) || isBlank(parsed.getRefreshToken())) {
                // A 2xx without a complete rotated pair is unusable: treat it as a
                // failure rather than risk reusing a refresh token the server burned.
                return Outcome.failure(ApiError.unparseable(response.code(), null));
            }
            return Outcome.success(toRotated(parsed));
        } catch (IOException e) {
            // A transport failure leaves the old refresh token valid server-side, so
            // the session is NOT wiped here; the caller surfaces a retry instead.
            return Outcome.failure(errorMapper.map(e));
        }
    }

    @NonNull
    private Session toRotated(@NonNull AuthResponse parsed) {
        long expiresIn = parsed.getExpiresIn() > 0 ? parsed.getExpiresIn() : DEFAULT_ACCESS_TTL_SECONDS;
        long refreshTtl = parsed.getRefreshExpiresIn() > 0
                ? parsed.getRefreshExpiresIn()
                : DEFAULT_REFRESH_TTL_SECONDS;

        String username = "";
        Role role = Role.EMPLOYEE;
        if (parsed.getUser() != null && parsed.getUser().getUsername() != null) {
            username = parsed.getUser().getUsername();
            role = Role.fromNullable(parsed.getUser().getRole());
        }
        return new Session(
                0L,
                username,
                role,
                false,
                parsed.getAccessToken(),
                parsed.getRefreshToken(),
                TokenRefresher.expiryFromNow(expiresIn),
                TokenRefresher.expiryFromNow(refreshTtl));
    }

    /**
     * Reads only the {@code code} of the error envelope. Anything else is a
     * status-based fallback; a failed refresh always means "cannot continue".
     */
    @NonNull
    private ApiError mapFailure(@NonNull Response response) {
        String code = HttpJson.peekErrorCode(response.body());
        if (code == null) {
            return ApiError.unparseable(response.code(), null);
        }
        String message = ApiErrorCodes.INVALID_REFRESH_TOKEN.equals(code)
                ? "Your session has expired. Please sign in again."
                : "Could not refresh your session.";
        return new ApiError(response.code(), code, message, null, null);
    }

    private static boolean isBlank(@Nullable String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Minimal JSON string escape; refresh tokens are opaque server-issued strings. */
    private static String escapeJson(@NonNull String raw) {
        StringBuilder out = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }
}