package com.larv.pharmacy.common.util;

import com.larv.pharmacy.common.exception.InvalidRequestException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;

/**
 * Date-range helpers for reporting. Public {@code from}/{@code to} parameters
 * are <b>inclusive</b> dates; internally everything is converted to a half-open
 * instant interval {@code [start, end)} so a "to" date of 2026-09-29 includes
 * every sale made during that day.
 */
public final class DateRangeUtil {

    private DateRangeUtil() {
    }

    /** Inclusive local-date range. */
    public record DateRange(LocalDate from, LocalDate to) {

        public DateRange {
            if (from == null || to == null) {
                throw new InvalidRequestException("INVALID_DATE_RANGE", "Both 'from' and 'to' dates are required");
            }
            if (from.isAfter(to)) {
                throw new InvalidRequestException("INVALID_DATE_RANGE",
                        "'from' date must not be after 'to' date");
            }
        }

        public java.time.Instant startInstant(ZoneId zone) {
            return from.atStartOfDay(zone).toInstant();
        }

        /** Exclusive end instant: beginning of the day after {@code to}. */
        public java.time.Instant endExclusiveInstant(ZoneId zone) {
            return to.plusDays(1).atStartOfDay(zone).toInstant();
        }
    }

    /**
     * Resolves a report period. Supported presets:
     * today, yesterday, this_week, last_week, this_month, last_month.
     * Explicit {@code from}/{@code to} take precedence over the preset;
     * when nothing is provided the range defaults to a single day (today).
     */
    public static DateRange resolve(String preset, LocalDate from, LocalDate to, LocalDate today, ZoneId zone) {
        if (from != null || to != null) {
            // Only 'to' given -> the single day 'to'; only 'from' -> [from, today].
            LocalDate effectiveFrom = from != null ? from : (to != null ? to : today);
            LocalDate effectiveTo = to != null ? to : today;
            return new DateRange(effectiveFrom, effectiveTo);
        }
        if (preset == null || preset.isBlank()) {
            return new DateRange(today, today);
        }
        return fromPreset(preset.trim().toLowerCase(), today, zone);
    }

    public static DateRange fromPreset(String preset, LocalDate today, ZoneId zone) {
        return switch (preset) {
            case "today" -> new DateRange(today, today);
            case "yesterday" -> new DateRange(today.minusDays(1), today.minusDays(1));
            case "this_week" -> {
                LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new DateRange(monday, monday.plusDays(6));
            }
            case "last_week" -> {
                LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
                yield new DateRange(monday, monday.plusDays(6));
            }
            case "this_month" -> new DateRange(today.withDayOfMonth(1), today.with(TemporalAdjusters.lastDayOfMonth()));
            case "last_month" -> {
                LocalDate first = today.withDayOfMonth(1).minusMonths(1);
                yield new DateRange(first, first.with(TemporalAdjusters.lastDayOfMonth()));
            }
            default -> throw new InvalidRequestException("INVALID_PERIOD",
                    "Unknown period preset: " + preset
                            + ". Supported: today, yesterday, this_week, last_week, this_month, last_month");
        };
    }

    /** Range covering the whole ISO week (Monday..Sunday) that contains {@code anyDate}. */
    public static DateRange weekContaining(LocalDate anyDate) {
        LocalDate monday = anyDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new DateRange(monday, monday.plusDays(6));
    }

    /** Range covering the given calendar month. */
    public static DateRange monthOf(int year, int month) {
        if (month < 1 || month > 12) {
            throw new InvalidRequestException("INVALID_MONTH", "Month must be between 1 and 12");
        }
        LocalDate first = LocalDate.of(year, month, 1);
        return new DateRange(first, first.with(TemporalAdjusters.lastDayOfMonth()));
    }
}
