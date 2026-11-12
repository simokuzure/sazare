package com.sazare.service.review;

import com.sazare.dto.ReviewSchedulingEvent;
import com.sazare.entity.ReviewCard;
import io.github.openspacedrepetition.Card;
import io.github.openspacedrepetition.Rating;
import io.github.openspacedrepetition.Scheduler;
import io.github.openspacedrepetition.State;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class FsrsScheduler {

    public static final double DESIRED_RETENTION = 0.95;

    private final Scheduler scheduler = Scheduler.builder()
            .desiredRetention(DESIRED_RETENTION)
            .learningSteps(new Duration[0])
            .relearningSteps(new Duration[0])
            .enableFuzzing(false)
            .build();

    public FsrsResult schedule(
            ReviewCard current,
            int quality,
            List<ReviewSchedulingEvent> history,
            LocalDateTime occurredAt
    ) {
        Rating rating = toRating(quality);
        Card memory = Card.builder().cardId(0).build();
        if (current.getFsrsStability() != null && current.getFsrsDifficulty() != null) {
            memory.setState(State.REVIEW);
            memory.setStability(current.getFsrsStability());
            memory.setDifficulty(current.getFsrsDifficulty());
            memory.setLastReview(studyDay(current.getLastReviewedAt()));
        } else {
            for (ReviewSchedulingEvent event : history) {
                memory = review(memory, toRating(event.quality()), studyDay(event.reviewedAt()));
            }
        }

        Instant reviewDate = studyDay(occurredAt);
        Card updated = review(memory, rating, reviewDate);
        int intervalDays = Math.toIntExact(Duration.between(reviewDate, updated.getDue()).toDays());
        boolean passed = rating != Rating.AGAIN;
        return new FsrsResult(
                current.getEaseFactor(), passed ? current.getRepetitionCount() + 1 : 0,
                intervalDays, current.getLapseCount() + (passed ? 0 : 1),
                updated.getStability(), updated.getDifficulty(), rating.getValue());
    }

    private Card review(Card memory, Rating rating, Instant reviewDate) {
        if (memory.getLastReview() != null && reviewDate.isBefore(memory.getLastReview())) {
            throw new IllegalArgumentException("复习日期不能早于上一条复习记录");
        }
        return scheduler.reviewCard(memory, rating, reviewDate).card();
    }

    private Rating toRating(int quality) {
        if (quality < 0 || quality > 5) {
            throw new IllegalArgumentException("复习质量分必须在0到5之间");
        }
        return quality >= 3 ? Rating.GOOD : Rating.AGAIN;
    }

    private Instant studyDay(LocalDateTime dateTime) {
        // 应用按日历日安排 07:00 到期；UTC 午夜仅作为 FSRS 计算日期差的基准。
        return dateTime.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
