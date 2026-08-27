package com.jt.learning.vo;

public record LearningStatisticsVO(
        LearningStatisticsCheckInOverviewVO checkInOverview,
        LearningStatisticsPeriodVO period,
        LearningStatisticsPracticeVO translation,
        LearningStatisticsPracticeVO correction,
        LearningStatisticsReviewOverviewVO reviewOverview
) {
}
