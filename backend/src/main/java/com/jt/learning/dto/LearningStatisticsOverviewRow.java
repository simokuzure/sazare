package com.jt.learning.dto;

import java.math.BigDecimal;

public record LearningStatisticsOverviewRow(
        Long answerCount,
        Long reviewedAnswerCount,
        BigDecimal averageTotalScore
) {
}
