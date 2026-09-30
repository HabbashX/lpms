package com.larv.pharmacy.common.util;

import com.larv.pharmacy.common.exception.InvalidRequestException;
import com.larv.pharmacy.common.util.DateRangeUtil.DateRange;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateRangeUtilTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30); // Wednesday

    @Test
    void defaultsToTodayWhenNothingProvided() {
        DateRange range = DateRangeUtil.resolve(null, null, null, TODAY, ZONE);
        assertThat(range.from()).isEqualTo(TODAY);
        assertThat(range.to()).isEqualTo(TODAY);
    }

    @Test
    void explicitFromToWinsOverPreset() {
        DateRange range = DateRangeUtil.resolve("this_month",
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 10), TODAY, ZONE);
        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(range.to()).isEqualTo(LocalDate.of(2026, 1, 10));
    }

    @Test
    void partialRangeFallsBackToToday() {
        DateRange fromOnly = DateRangeUtil.resolve(null, LocalDate.of(2026, 9, 1), null, TODAY, ZONE);
        assertThat(fromOnly.from()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(fromOnly.to()).isEqualTo(TODAY);

        // Only 'to' given: a single past day must resolve to [to, to], not [today, to].
        DateRange toOnly = DateRangeUtil.resolve(null, null, LocalDate.of(2026, 9, 20), TODAY, ZONE);
        assertThat(toOnly.from()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(toOnly.to()).isEqualTo(LocalDate.of(2026, 9, 20));
    }

    @Test
    void thisWeekIsMondayToSunday() {
        DateRange range = DateRangeUtil.resolve("this_week", null, null, TODAY, ZONE);
        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 9, 28)); // Monday
        assertThat(range.to()).isEqualTo(LocalDate.of(2026, 10, 4));   // Sunday
    }

    @Test
    void lastWeekIsPreviousMondayToSunday() {
        DateRange range = DateRangeUtil.resolve("last_week", null, null, TODAY, ZONE);
        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(range.to()).isEqualTo(LocalDate.of(2026, 9, 27));
    }

    @Test
    void thisAndLastMonthBoundaries() {
        DateRange thisMonth = DateRangeUtil.resolve("this_month", null, null, TODAY, ZONE);
        assertThat(thisMonth.from()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(thisMonth.to()).isEqualTo(LocalDate.of(2026, 9, 30));

        DateRange lastMonth = DateRangeUtil.resolve("last_month", null, null, TODAY, ZONE);
        assertThat(lastMonth.from()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(lastMonth.to()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    void presetIsCaseInsensitive() {
        DateRange range = DateRangeUtil.resolve(" Yesterday ", null, null, TODAY, ZONE);
        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 9, 29));
        assertThat(range.to()).isEqualTo(LocalDate.of(2026, 9, 29));
    }

    @Test
    void unknownPresetThrows() {
        assertThatThrownBy(() -> DateRangeUtil.resolve("forever", null, null, TODAY, ZONE))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("forever");
    }

    @Test
    void invalidInclusiveRangeThrows() {
        assertThatThrownBy(() -> new DateRange(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> new DateRange(null, LocalDate.of(2026, 9, 1)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void endInstantIsExclusiveNextDay() {
        DateRange range = new DateRange(LocalDate.of(2026, 9, 29), LocalDate.of(2026, 9, 29));
        assertThat(range.startInstant(ZONE)).isEqualTo(LocalDate.of(2026, 9, 29).atStartOfDay(ZONE).toInstant());
        assertThat(range.endExclusiveInstant(ZONE))
                .isEqualTo(LocalDate.of(2026, 9, 30).atStartOfDay(ZONE).toInstant());
    }

    @Test
    void weekContainingHandlesSundayAsEndOfWeek() {
        DateRange range = DateRangeUtil.weekContaining(LocalDate.of(2026, 9, 27)); // Sunday
        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(range.to()).isEqualTo(LocalDate.of(2026, 9, 27));
    }

    @Test
    void monthOfRejectsInvalidMonth() {
        assertThatThrownBy(() -> DateRangeUtil.monthOf(2026, 13))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> DateRangeUtil.monthOf(2026, 0))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void monthOfHandlesLeapYear() {
        DateRange range = DateRangeUtil.monthOf(2028, 2);
        assertThat(range.from()).isEqualTo(LocalDate.of(2028, 2, 1));
        assertThat(range.to()).isEqualTo(LocalDate.of(2028, 2, 29));
    }
}
