package com.sazare.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LearningStatisticsDailyTrendVO(
        LocalDate date,
        Long attemptCount,
        BigDecimal averageTotalScore
) {
}
