package com.jt.learning.vo;

import java.math.BigDecimal;

public record LearningStatisticsOverviewVO(
        Long answerCount,
        Long reviewedAnswerCount,
        BigDecimal averageTotalScore,
        Long confirmedErrorCount
) {
}
