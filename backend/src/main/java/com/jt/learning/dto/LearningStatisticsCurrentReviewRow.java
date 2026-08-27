package com.jt.learning.dto;

public record LearningStatisticsCurrentReviewRow(
        Long dueCardCount,
        Long inProgressCardCount,
        Long masteredCardCount
) {
}
