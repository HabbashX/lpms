package com.lpms.core.network.json;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * {@code Instant} ⇄ ISO-8601. The backend emits UTC instants such as
 * {@code 2026-09-29T10:15:30Z} (Jackson may include fractional seconds).
 *
 * <p>Null-safe: an absent or JSON-null field becomes Java null, never
 * {@code Instant.EPOCH}.</p>
 */
public final class InstantAdapter extends TypeAdapter<Instant> {

    @Nullable
    @Override
    public Instant read(@NonNull JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
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
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            try {
                return Instant.parse(value + "Z");
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    @Override
    public void write(@NonNull JsonWriter out, @Nullable Instant value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value.toString());
        }
    }
}