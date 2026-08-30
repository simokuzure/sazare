package com.sazare.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewDueAtCalculatorTest {

    @Test
    void shouldAlignOneDayIntervalsToSevenInTheMorningAcrossAllCreationTimes() {
        assertThat(ReviewDueAtCalculator.calculate(LocalDateTime.of(2026, 8, 7, 0, 0), 1))
                .isEqualTo(LocalDateTime.of(2026, 8, 8, 7, 0));
        assertThat(ReviewDueAtCalculator.calculate(LocalDateTime.of(2026, 8, 7, 6, 59), 1))
                .isEqualTo(LocalDateTime.of(2026, 8, 8, 7, 0));
        assertThat(ReviewDueAtCalculator.calculate(LocalDateTime.of(2026, 8, 7, 7, 0), 1))
                .isEqualTo(LocalDateTime.of(2026, 8, 8, 7, 0));
        assertThat(ReviewDueAtCalculator.calculate(LocalDateTime.of(2026, 8, 7, 23, 59), 1))
                .isEqualTo(LocalDateTime.of(2026, 8, 8, 7, 0));
    }

    @Test
    void shouldKeepSm2IntervalDaysAndAlignToSevenAcrossMonthBoundary() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 27, 23, 59);

        assertThat(ReviewDueAtCalculator.calculate(occurredAt, 6))
                .isEqualTo(LocalDateTime.of(2026, 9, 2, 7, 0));
    }

    @Test
    void shouldRejectInvalidIntervalDays() {
        assertThatThrownBy(() -> ReviewDueAtCalculator.calculate(LocalDateTime.of(2026, 8, 7, 7, 0), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("复习间隔天数必须大于等于1");
    }
}
