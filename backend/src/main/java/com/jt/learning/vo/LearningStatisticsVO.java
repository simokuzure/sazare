package com.jt.learning.vo;

import java.util.List;

public record LearningStatisticsVO(
        LearningStatisticsPeriodVO period,
        LearningStatisticsOverviewVO overview,
        List<LearningStatisticsDailyTrendVO> dailyTrends,
        LearningStatisticsScoreDimensionsVO scoreDimensions,
        List<LearningStatisticsWeaknessVO> weaknesses,
        LearningStatisticsReviewOverviewVO reviewOverview,
        LearningStatisticsOverviewVO correctionOverview,
        List<LearningStatisticsDailyTrendVO> correctionDailyTrends,
        LearningStatisticsScoreDimensionsVO correctionScoreDimensions
) {
}
