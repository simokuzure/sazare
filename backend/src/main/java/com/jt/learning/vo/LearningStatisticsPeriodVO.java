package com.jt.learning.vo;

import java.time.LocalDate;

public record LearningStatisticsPeriodVO(
        String range,
        LocalDate startDate,
        LocalDate endDate
) {
}
