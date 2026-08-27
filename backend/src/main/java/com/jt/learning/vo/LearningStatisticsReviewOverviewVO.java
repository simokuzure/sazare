package com.jt.learning.vo;

import java.math.BigDecimal;

public record LearningStatisticsReviewOverviewVO(
        Long dueCardCount,
        Long inProgressCardCount,
        Long masteredCardCount,
        Long periodReviewAttemptCount,
        BigDecimal periodReviewPassRate
) {
}
