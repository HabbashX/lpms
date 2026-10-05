package com.lpms.core.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;

import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Minimal peeking helpers so the OkHttp layer can decide <em>what</em> to do with a
 * response before Retrofit converts it.
 *
 * <p>The OkHttp authenticator must read only the {@code code} field; parsing the
 * full error envelope there would be wasteful and would risk touching the body
 * stream that Retrofit still needs.</p>
 */
public final class HttpJson {

    private HttpJson() {
    }

    /**
     * Reads the {@code code} field from a backend error body without consuming the
     * stream for the caller (OkHttp buffers the body, so this is safe).
     *
     * @return the code, or null when the body is absent/empty/not a JSON object.
     */
    @Nullable
    public static String peekErrorCode(@Nullable ResponseBody body) {
        if (body == null) {
            return null;
        }
        try {
            JsonObject root = parseObject(body);
            if (root == null || !root.has("code") || root.get("code").isJsonNull()) {
                return null;
            }
            return root.get("code").getAsString();
        } catch (JsonSyntaxException | IllegalStateException | NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    private static JsonObject parseObject(@NonNull ResponseBody body) {
        try {
            return JsonParser.parseString(body.string()).getAsJsonObject();
        } catch (IOException | JsonSyntaxException | IllegalStateException
                 | UnsupportedOperationException e) {
            return null;
        }
    }

    /**
     * Same as {@link #peekErrorCode(ResponseBody)} but reads through
     * {@link Response#peekBody(long)}, which buffers a copy <em>without</em>
     * consuming the body. Required inside an {@link okhttp3.Authenticator}: the
     * original response is handed back to Retrofit afterwards, which still needs to
     * parse the error envelope.
     */
    @Nullable
    public static String peekErrorCode(@Nullable Response response) {
        if (response == null || response.body() == null) {
            return null;
        }
        try {
            return peekErrorCode(response.peekBody(MAX_ERROR_BODY_BYTES));
        } catch (IOException e) {
            return null;
        }
    }

    /** Upper bound on how much of an error body we are willing to buffer. */
    public static final long MAX_ERROR_BODY_BYTES = 8_192L;
}