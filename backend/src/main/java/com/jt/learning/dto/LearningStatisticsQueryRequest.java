package com.jt.learning.dto;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record LearningStatisticsQueryRequest(
        @Pattern(
                regexp = "LAST_7_DAYS|LAST_30_DAYS|LAST_90_DAYS|CUSTOM",
                message = "range 只能是 LAST_7_DAYS、LAST_30_DAYS、LAST_90_DAYS、CUSTOM"
        )
        String range,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate startDate,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate endDate
) {
    public LearningStatisticsQueryRequest {
        range = range == null || range.isBlank() ? "LAST_30_DAYS" : range.trim();
    }
}
