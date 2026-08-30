package com.sazare.vo;

public record LearningStatisticsCheckInOverviewVO(
        Long currentStreakDays,
        Long totalCheckInDays
) {
}
