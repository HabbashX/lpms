package com.lpms.core.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/**
 * Date/time rendering for the UI.
 *
 * <p>Everything arrives from the backend as ISO-8601 UTC {@code Instant} or a
 * {@code yyyy-MM-dd} {@code LocalDate} and is displayed in the <b>device</b> timezone
 * with the <b>device</b> locale, so an Arabic device shows Arabic-Indic digits.</p>
 */
public final class Dates {

    private static Clock clock = Clock.systemDefaultZone();

    private Dates() {
    }

    /** Test seam: fixes "now" and the display zone. */
    public static void useClock(@NonNull Clock replacement) {
        clock = replacement;
    }

    @NonNull
    public static Clock clock() {
        return clock;
    }

    /** e.g. {@code 29 Sep 2026, 10:15} in the device locale and timezone. */
    @NonNull
    public static String dateTime(@NonNull Context context, @Nullable Instant instant) {
        if (instant == null) {
            return "—";
        }
        DateTimeFormatter formatter = DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(localeOf(context));
        return formatter.format(LocalDateTime.ofInstant(instant, zone()));
    }

    /** Time only, e.g. {@code 10:15}. */
    @NonNull
    public static String time(@NonNull Context context, @Nullable Instant instant) {
        if (instant == null) {
            return "—";
        }
        DateTimeFormatter formatter = DateTimeFormatter
                .ofLocalizedTime(FormatStyle.SHORT)
                .withLocale(localeOf(context));
        return formatter.format(LocalDateTime.ofInstant(instant, zone()));
    }

    /** e.g. {@code 29/09/2026} for the device locale. */
    @NonNull
    public static String date(@NonNull Context context, @Nullable LocalDate date) {
        if (date == null) {
            return "—";
        }
        return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(localeOf(context)));
    }

    /** Compact label for lists and chart axes, e.g. {@code 29 Sep}. */
    @NonNull
    public static String shortDate(@NonNull Context context, @Nullable LocalDate date) {
        if (date == null) {
            return "—";
        }
        return date.format(DateTimeFormatter.ofPattern("d MMM", localeOf(context)));
    }

    /** Relative label for audit and sales rows: "just now", "3h ago", "2 days ago". */
    @NonNull
    public static String relative(@NonNull Context context, @Nullable Instant instant) {
        if (instant == null) {
            return "—";
        }
        long minutes = java.time.Duration.between(instant, Instant.now(clock)).toMinutes();
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h ago";
        }
        long days = hours / 24;
        if (days < 7) {
            return days + "d ago";
        }
        return date(context, LocalDateTime.ofInstant(instant, zone()).toLocalDate());
    }

    /** Wire format for query parameters: {@code yyyy-MM-dd}, always ASCII digits. */
    @NonNull
    public static String toQueryDate(@Nullable LocalDate date) {
        return date == null ? "" : date.toString();
    }

    @NonNull
    private static Locale localeOf(@NonNull Context context) {
        android.content.res.Configuration config = context.getResources().getConfiguration();
        Locale locale = config.getLocales().isEmpty() ? Locale.getDefault() : config.getLocales().get(0);
        return locale;
    }

    @NonNull
    private static ZoneId zone() {
        return clock.getZone();
    }
}
