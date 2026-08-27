package com.jt.learning.dto;

import java.math.BigDecimal;

public record LearningStatisticsOverviewRow(
        Long attemptCount,
        BigDecimal averageTotalScore
) {
}
