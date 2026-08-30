package com.sazare.dto;

public record LearningStatisticsCurrentReviewRow(
        Long dueCardCount,
        Long inProgressCardCount,
        Long masteredCardCount
) {
}
