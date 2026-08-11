package com.jt.learning.service.review;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Sm2SchedulerTest {

    private final Sm2Scheduler scheduler = new Sm2Scheduler();

    @Test
    void shouldCalculateAllSixQualityFactors() {
        assertThat(schedule(0).easeFactor()).isEqualByComparingTo("1.7000");
        assertThat(schedule(1).easeFactor()).isEqualByComparingTo("1.9600");
        assertThat(schedule(2).easeFactor()).isEqualByComparingTo("2.1800");
        assertThat(schedule(3).easeFactor()).isEqualByComparingTo("2.3600");
        assertThat(schedule(4).easeFactor()).isEqualByComparingTo("2.5000");
        assertThat(schedule(5).easeFactor()).isEqualByComparingTo("2.6000");
    }

    @Test
    void shouldApplyMinimumEaseFactor() {
        Sm2Result result = scheduler.schedule(new BigDecimal("1.3000"), 0, 1, 0, 0);

        assertThat(result.easeFactor()).isEqualByComparingTo("1.3000");
    }

    @Test
    void shouldUseOneSixAndMultipliedIntervalSequence() {
        Sm2Result first = scheduler.schedule(new BigDecimal("2.5000"), 0, 1, 0, 5);
        Sm2Result second = scheduler.schedule(first.easeFactor(), first.repetitionCount(), first.intervalDays(), 0, 5);
        Sm2Result third = scheduler.schedule(second.easeFactor(), second.repetitionCount(), second.intervalDays(), 0, 5);

        assertThat(first.intervalDays()).isEqualTo(1);
        assertThat(second.intervalDays()).isEqualTo(6);
        assertThat(third.intervalDays()).isEqualTo(16);
        assertThat(third.repetitionCount()).isEqualTo(3);
    }

    @Test
    void failureShouldResetRepetitionAndIncrementLapse() {
        Sm2Result result = scheduler.schedule(new BigDecimal("2.5000"), 4, 20, 2, 2);

        assertThat(result.repetitionCount()).isZero();
        assertThat(result.intervalDays()).isEqualTo(1);
        assertThat(result.lapseCount()).isEqualTo(3);
    }

    @Test
    void shouldRejectQualityOutsideRange() {
        assertThatThrownBy(() -> scheduler.schedule(new BigDecimal("2.5000"), 0, 1, 0, 6))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Sm2Result schedule(int quality) {
        return scheduler.schedule(new BigDecimal("2.5000"), 0, 1, 0, quality);
    }
}
