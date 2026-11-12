package com.sazare.service.review;

import com.sazare.dto.ReviewSchedulingEvent;
import com.sazare.entity.ReviewCard;
import com.sazare.util.ReviewDueAtCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FsrsSchedulerTest {

    private final FsrsScheduler scheduler = new FsrsScheduler();

    @ParameterizedTest
    @CsvSource({"0,1", "1,1", "2,1", "3,3", "4,3", "5,3"})
    void mapsOnlyPassOrFailureToFsrs(int quality, int rating) {
        FsrsResult result = scheduler.schedule(card(), quality, List.of(), LocalDateTime.now());
        assertThat(result.rating()).isEqualTo(rating);
        assertThat(result.intervalDays()).isGreaterThanOrEqualTo(1);
        assertThat(result.easeFactor()).isEqualByComparingTo("2.5000");
    }

    @ParameterizedTest
    @CsvSource({"6,23", "10,187"})
    void reproducesCalendarDayCycleWithoutFailures(int successes, int expectedDays) {
        ReviewCard card = card();
        LocalDateTime start = LocalDateTime.of(2026, 12, 31, 23, 59);
        apply(card, scheduler.schedule(card, 2, List.of(), start), start);
        LocalDateTime next = ReviewDueAtCalculator.calculate(start, card.getIntervalDays());
        for (int i = 0; i < successes; i++) {
            LocalDateTime reviewedAt = next;
            apply(card, scheduler.schedule(card, 4, List.of(), reviewedAt), reviewedAt);
            next = ReviewDueAtCalculator.calculate(reviewedAt, card.getIntervalDays());
        }
        assertThat(ChronoUnit.DAYS.between(start.toLocalDate(), card.getLastReviewedAt().toLocalDate()))
                .isEqualTo(expectedDays);
        assertThat(card.getLastReviewedAt().toLocalTime()).isEqualTo(java.time.LocalTime.of(7, 0));
    }

    @Test
    void replaysAllPastEventsBeforeSchedulingNewEvent() {
        LocalDateTime first = LocalDateTime.of(2026, 10, 1, 22, 0);
        LocalDateTime second = first.plusDays(1);
        ReviewCard initialized = card();
        apply(initialized, scheduler.schedule(initialized, 2, List.of(), first), first);
        apply(initialized, scheduler.schedule(initialized, 4, List.of(), second), second);
        FsrsResult expected = scheduler.schedule(initialized, 4, List.of(), second.plusDays(3));
        FsrsResult replayed = scheduler.schedule(card(), 4, List.of(
                new ReviewSchedulingEvent(2, first), new ReviewSchedulingEvent(4, second)), second.plusDays(3));
        assertThat(replayed.stability()).isEqualTo(expected.stability());
        assertThat(replayed.difficulty()).isEqualTo(expected.difficulty());
        assertThat(replayed.intervalDays()).isEqualTo(expected.intervalDays());
    }

    @Test
    void calendarDaysHandleSameDayEarlyAndOverdueReviews() {
        ReviewCard card = card();
        LocalDateTime first = LocalDateTime.of(2026, 12, 31, 23, 59);
        apply(card, scheduler.schedule(card, 4, List.of(), first), first);
        FsrsResult sameDay = scheduler.schedule(card, 4, List.of(), first.minusHours(1));
        FsrsResult nextDay = scheduler.schedule(card, 4, List.of(), first.plusMinutes(2));
        FsrsResult overdue = scheduler.schedule(card, 4, List.of(), first.plusDays(10));
        assertThat(sameDay.intervalDays()).isGreaterThanOrEqualTo(1);
        assertThat(nextDay.stability()).isGreaterThan(sameDay.stability());
        assertThat(overdue.stability()).isGreaterThan(nextDay.stability());
    }

    @Test
    void failureUsesMemoryInsteadOfAlwaysResettingIntervalToOne() {
        ReviewCard card = card();
        LocalDateTime now = LocalDateTime.now();
        card.setFsrsStability(100.0);
        card.setFsrsDifficulty(5.0);
        card.setLastReviewedAt(now.minusDays(60));
        card.setRepetitionCount(4);
        FsrsResult result = scheduler.schedule(card, 2, List.of(), now);
        assertThat(result.intervalDays()).isGreaterThan(1);
        assertThat(result.stability()).isLessThan(100.0);
        assertThat(result.repetitionCount()).isZero();
        assertThat(result.lapseCount()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidQualityAndOutOfOrderHistory() {
        LocalDateTime now = LocalDateTime.now();
        assertThatThrownBy(() -> scheduler.schedule(card(), 6, List.of(), now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scheduler.schedule(card(), 4,
                List.of(new ReviewSchedulingEvent(2, now.plusDays(1))), now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ReviewCard card() {
        ReviewCard card = new ReviewCard();
        card.setEaseFactor(new BigDecimal("2.5000"));
        card.setRepetitionCount(0);
        card.setIntervalDays(1);
        card.setLapseCount(0);
        return card;
    }

    private void apply(ReviewCard card, FsrsResult result, LocalDateTime now) {
        card.setFsrsStability(result.stability());
        card.setFsrsDifficulty(result.difficulty());
        card.setRepetitionCount(result.repetitionCount());
        card.setIntervalDays(result.intervalDays());
        card.setLapseCount(result.lapseCount());
        card.setLastReviewedAt(now);
    }
}
