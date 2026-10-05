package com.lpms.core.network.json;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * {@code LocalDate} ⇄ {@code yyyy-MM-dd}, matching the backend's
 * {@code java.time.LocalDate} Jackson serialisation. Used for expiration dates
 * and for the {@code from}/{@code to} query parameters.
 */
public final class LocalDateAdapter extends TypeAdapter<LocalDate> {

    @Nullable
    @Override
    public LocalDate read(@NonNull JsonReader in) throws IOException {
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
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @Override
    public void write(@NonNull JsonWriter out, @Nullable LocalDate value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value.toString());
        }
    }
}