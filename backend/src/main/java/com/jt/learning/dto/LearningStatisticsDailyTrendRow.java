package com.jt.learning.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LearningStatisticsDailyTrendRow(
        LocalDate day,
        Long attemptCount,
        BigDecimal averageTotalScore
) {
}
