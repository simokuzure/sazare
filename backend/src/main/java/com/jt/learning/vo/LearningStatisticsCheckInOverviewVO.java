package com.jt.learning.vo;

public record LearningStatisticsCheckInOverviewVO(
        Long currentStreakDays,
        Long totalCheckInDays
) {
}
