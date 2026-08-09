package com.jt.learning.dto;

public record LearningStatisticsCurrentReviewRow(
        Long dueCardCount,
        Long activeCardCount,
        Long masteredCardCount
) {
}
