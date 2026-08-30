package com.sazare.dto;

import java.math.BigDecimal;

public record LearningStatisticsOverviewRow(
        Long attemptCount,
        BigDecimal averageTotalScore
) {
}
