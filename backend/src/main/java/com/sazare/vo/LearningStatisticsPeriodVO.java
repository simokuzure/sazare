package com.sazare.vo;

import java.time.LocalDate;

public record LearningStatisticsPeriodVO(
        String range,
        LocalDate startDate,
        LocalDate endDate
) {
}
