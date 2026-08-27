package com.jt.learning.vo;

import java.math.BigDecimal;
import java.util.List;

public record LearningStatisticsPracticeVO(
        Long attemptCount,
        BigDecimal averageTotalScore,
        List<LearningStatisticsDailyTrendVO> dailyTrends,
        LearningStatisticsScoreDimensionsVO scoreDimensions
) {
}
