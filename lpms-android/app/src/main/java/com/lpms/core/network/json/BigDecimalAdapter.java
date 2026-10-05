package com.lpms.core.network.json;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * {@code BigDecimal} ⇄ JSON. Every money and quantity value in the app goes
 * through this adapter — {@code double}/{@code float} are never used.
 *
 * <p>Accepts both a JSON number ({@code 10.50}, the normal backend shape) and a
 * JSON string ({@code "10.50"}) defensively, so a Jackson
 * {@code WRITE_NUMBERS_AS_STRINGS} switch cannot corrupt prices. Null in, null
 * out; blank and malformed strings become null rather than throwing, because a
 * single bad field must not blank an entire screen.</p>
 *
 * <p>Scale is preserved exactly as sent: {@code 10.50} deserialises to a
 * BigDecimal with scale 2, which keeps display and re-serialisation faithful.</p>
 */
public final class BigDecimalAdapter extends TypeAdapter<BigDecimal> {

    @Nullable
    @Override
    public BigDecimal read(@NonNull JsonReader in) throws IOException {
        JsonToken token = in.peek();
        if (token == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        if (token == JsonToken.BOOLEAN) {
            in.nextBoolean();
            return null;
        }
        String raw = in.nextString();
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void write(@NonNull JsonWriter out, @Nullable BigDecimal value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            // Plain '.' decimal point, never locale-dependent: the backend contract
            // requires dot decimals regardless of the device locale.
            out.value(value.toPlainString());
        }
    }
}