package com.larv.pharmacy.common.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyUtilTest {

    @Test
    void round2RoundsHalfUpToTwoDecimals() {
        assertThat(MoneyUtil.round2(new BigDecimal("1.005"))).isEqualByComparingTo("1.01");
        assertThat(MoneyUtil.round2(new BigDecimal("1.004"))).isEqualByComparingTo("1.00");
        assertThat(MoneyUtil.round2(new BigDecimal("10"))).isEqualByComparingTo("10.00");
        assertThat(MoneyUtil.round2(new BigDecimal("10")).scale()).isEqualTo(2);
    }

    @Test
    void round2TreatsNullAsZero() {
        assertThat(MoneyUtil.round2(null)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void round4KeepsFourDecimals() {
        assertThat(MoneyUtil.round4(new BigDecimal("2.6875"))).isEqualByComparingTo("2.6875");
        assertThat(MoneyUtil.round4(new BigDecimal("2.68755"))).isEqualByComparingTo("2.6876");
        assertThat(MoneyUtil.round4(new BigDecimal("2.5")).scale()).isEqualTo(4);
    }

    @Test
    void multiplyIsNullSafe() {
        assertThat(MoneyUtil.multiply(new BigDecimal("2.50"), 4)).isEqualByComparingTo("10.00");
        assertThat(MoneyUtil.multiply(null, 4)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void divideReturnsZeroForZeroDenominator() {
        assertThat(MoneyUtil.divide(BigDecimal.TEN, BigDecimal.ZERO, 4)).isEqualByComparingTo("0.0000");
        assertThat(MoneyUtil.divide(null, null, 2)).isEqualByComparingTo("0.00");
    }

    @Test
    void divideRoundsAtRequestedScale() {
        assertThat(MoneyUtil.divide(new BigDecimal("10"), new BigDecimal("3"), 4))
                .isEqualByComparingTo("3.3333");
        assertThat(MoneyUtil.divide(new BigDecimal("10"), new BigDecimal("3"), 2))
                .isEqualByComparingTo("3.33");
    }

    @Test
    void isNegativeIsNullSafe() {
        assertThat(MoneyUtil.isNegative(new BigDecimal("-0.01"))).isTrue();
        assertThat(MoneyUtil.isNegative(BigDecimal.ZERO)).isFalse();
        assertThat(MoneyUtil.isNegative(null)).isFalse();
    }
}
