package com.lpms.core.network.json;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.lpms.BuildConfig;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The one Gson instance used by Retrofit, the error mapper and the OkHttp
 * authenticator.
 *
 * <p>Deliberate configuration:</p>
 * <ul>
 *   <li>Unknown JSON keys are ignored, mirroring the server's
 *       {@code fail-on-unknown-properties: false} — a backend field added later
 *       can never crash an older client.</li>
 *   <li>Missing/JSON-null fields stay null (no primitives defaulted to 0/false
 *       implicitly): DTOs use boxed types wherever "absent" is meaningful.</li>
 *   <li>{@code serializeNulls} is ON so optional fields are sent explicitly as
 *       JSON {@code null} instead of silently disappearing. Both are accepted by
 *       the backend, but explicit nulls make a PUT body self-describing (e.g.
 *       {@code unitSellingPrice: null} in {@code CreateSaleRequest} means "use the
 *       drug's default selling price") and keep request logs debuggable.</li>
 * </ul>
 */
public final class LpmsGson {

    private static final Gson INSTANCE = create();

    private LpmsGson() {
    }

    @NonNull
    public static Gson get() {
        return INSTANCE;
    }

    @NonNull
    public static Gson create() {
        return new GsonBuilder()
                .registerTypeAdapter(Instant.class, new InstantAdapter())
                .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
                .registerTypeAdapter(java.math.BigDecimal.class, new BigDecimalAdapter())
                .serializeNulls()
                .disableHtmlEscaping()
                .setLenient()
                .create();
    }

    /** True when this build may log request/response bodies. Never true in release. */
    public static boolean loggingEnabled() {
        return BuildConfig.ENABLE_NETWORK_LOGGING;
    }

    /** Marker used by reflection-free tests to assert field naming is stable. */
    public static boolean isSerializedNamePresent(Class<?> type, String fieldName) {
        try {
            return type.getDeclaredField(fieldName).isAnnotationPresent(SerializedName.class);
        } catch (NoSuchFieldException e) {
            return false;
        }
    }
}