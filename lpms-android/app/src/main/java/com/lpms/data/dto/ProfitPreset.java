package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * {@code preset} values accepted by {@code GET /reports/profit}:
 * {@code today, yesterday, this_week, last_week, this_month, last_month}.
 *
 * <p>An explicit {@code from}/{@code to} pair overrides the preset; sending nothing at
 * all defaults to today. Date ranges are inclusive.</p>
 */
public enum ProfitPreset {

    @SerializedName("today")
    TODAY("today"),

    @SerializedName("yesterday")
    YESTERDAY("yesterday"),

    @SerializedName("this_week")
    THIS_WEEK("this_week"),

    @SerializedName("last_week")
    LAST_WEEK("last_week"),

    @SerializedName("this_month")
    THIS_MONTH("this_month"),

    @SerializedName("last_month")
    LAST_MONTH("last_month");

    private final String wire;

    ProfitPreset(String wire) {
        this.wire = wire;
    }

    @NonNull
    public String wireValue() {
        return wire;
    }

    /** Inclusive {@code [from, to]} for this preset, evaluated in the device timezone. */
    @NonNull
    public DateRange resolve(@NonNull java.time.Clock clock) {
        LocalDate today = LocalDate.now(clock);
        switch (this) {
            case TODAY:
                return new DateRange(today, today);
            case YESTERDAY:
                return new DateRange(today.minusDays(1), today.minusDays(1));
            case THIS_WEEK:
                // ISO week: Monday → Sunday.
                return new DateRange(
                        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                        today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
            case LAST_WEEK: {
                LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate lastMonday = thisMonday.minusWeeks(1);
                return new DateRange(lastMonday, lastMonday.plusDays(6));
            }
            case THIS_MONTH:
                return new DateRange(today.withDayOfMonth(1), today.withDayOfMonth(
                        today.lengthOfMonth()));
            case LAST_MONTH:
            default: {
                LocalDate firstOfThisMonth = today.withDayOfMonth(1);
                LocalDate lastOfLastMonth = firstOfThisMonth.minusDays(1);
                return new DateRange(lastOfLastMonth.withDayOfMonth(1), lastOfLastMonth);
            }
        }
    }

    /** Inclusive date range sent as {@code from} / {@code to} (yyyy-MM-dd). */
    public static final class DateRange {

        private final LocalDate from;
        private final LocalDate to;

        public DateRange(@NonNull LocalDate from, @NonNull LocalDate to) {
            this.from = from;
            this.to = to;
        }

        @NonNull
        public LocalDate getFrom() {
            return from;
        }

        @NonNull
        public LocalDate getTo() {
            return to;
        }

        /** Inclusive day count; {@code 1} for a single-day range. */
        public long days() {
            return java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        }

        public boolean spansAtMost(int maxDays) {
            return days() <= maxDays;
        }

        /** True when {@code from} is after {@code to}; 400 {@code INVALID_DATE_RANGE}. */
        public boolean isInverted() {
            return from.isAfter(to);
        }

        @NonNull
        @Override
        public String toString() {
            return from + ".." + to;
        }
    }

    /** The default when the user has not chosen anything. */
    public static final ProfitPreset DEFAULT = TODAY;

    @NonNull
    public static ProfitPreset fromNullable(@Nullable String raw) {
        if (raw == null) {
            return DEFAULT;
        }
        for (ProfitPreset preset : values()) {
            if (preset.wire.equalsIgnoreCase(raw.trim())) {
                return preset;
            }
        }
        return DEFAULT;
    }
}