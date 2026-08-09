package com.jt.learning.vo;

import java.math.BigDecimal;

public record LearningStatisticsReviewOverviewVO(
        Long dueCardCount,
        Long activeCardCount,
        Long masteredCardCount,
        Long periodReviewAttemptCount,
        Long periodReviewPassCount,
        BigDecimal periodReviewPassRate,
        Long periodCompletedCycleCount
) {
}
