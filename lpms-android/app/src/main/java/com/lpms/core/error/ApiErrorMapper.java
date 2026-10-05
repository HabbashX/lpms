package com.lpms.core.error;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import javax.net.ssl.SSLException;

import okhttp3.ResponseBody;
import retrofit2.HttpException;
import retrofit2.Response;

/**
 * Turns any failure into exactly one {@link ApiError}.
 *
 * <p>Rules:</p>
 * <ul>
 *   <li>HTTP errors: parse the envelope and trust {@code code}; if the body is not
 *       a valid envelope, synthesise a status-based fallback ({@link ApiError#unparseable}).</li>
 *   <li>Never inspect message text. {@code code} is the only discriminator.</li>
 *   <li>The {@code errors} array is kept only for {@code VALIDATION_FAILED}/400.</li>
 * </ul>
 */
public final class ApiErrorMapper {

    private static final int MAX_ERROR_BODY_CHARS = 8_192;

    private final Gson gson;

    public ApiErrorMapper(@NonNull Gson gson) {
        this.gson = gson;
    }

    /** Maps a Retrofit {@link HttpException}-style failure (non-2xx response). */
    @NonNull
    public ApiError map(@NonNull Response<?> response) {
        int status = response.code();
        ApiErrorEnvelope envelope = parseEnvelope(response.errorBody());
        if (envelope == null) {
            return ApiError.unparseable(status, null);
        }

        String code = isBlank(envelope.getCode())
                ? ApiErrorCodes.UNKNOWN
                : envelope.getCode();
        String message = isBlank(envelope.getMessage())
                ? ApiError.unparseable(status, null).getMessage()
                : envelope.getMessage();

        java.util.List<FieldError> fieldErrors =
                (status == 400 && ApiErrorCodes.VALIDATION_FAILED.equals(code))
                        ? envelope.getErrors()
                        : null;

        int effectiveStatus = envelope.getStatus() != null ? envelope.getStatus() : status;
        return new ApiError(effectiveStatus, code, message, envelope.getPath(), fieldErrors);
    }

    /** Maps a thrown exception (transport, timeout, TLS, cancellation-as-error). */
    @NonNull
    public ApiError map(@NonNull Throwable throwable) {
        if (throwable instanceof HttpException) {
            return ApiError.unparseable(((HttpException) throwable).code(), null);
        }
        if (throwable instanceof SocketTimeoutException
                || throwable instanceof InterruptedIOException) {
            return ApiError.timeout("The server took too long to respond.");
        }
        if (throwable instanceof UnknownHostException) {
            return ApiError.network("Cannot reach the server. Check the connection.");
        }
        if (throwable instanceof SSLException) {
            return ApiError.network("Secure connection failed.");
        }
        if (throwable instanceof IOException) {
            return ApiError.network("Cannot reach the server. Check the connection.");
        }
        return ApiError.network(throwable.getMessage() == null
                ? "Unexpected error"
                : throwable.getMessage());
    }

    @Nullable
    private ApiErrorEnvelope parseEnvelope(@Nullable ResponseBody body) {
        if (body == null) {
            return null;
        }
        String raw;
        try {
            raw = body.string();
        } catch (IOException e) {
            return null;
        }
        if (raw == null || raw.trim().isEmpty() || raw.length() > MAX_ERROR_BODY_CHARS) {
            return null;
        }
        try {
            ApiErrorEnvelope parsed = gson.fromJson(raw, ApiErrorEnvelope.class);
            return parsed == null || parsed.getStatus() == null ? null : parsed;
        } catch (JsonSyntaxException | IllegalStateException e) {
            return null;
        }
    }

    private static boolean isBlank(@Nullable String s) {
        return s == null || s.trim().isEmpty();
    }
}