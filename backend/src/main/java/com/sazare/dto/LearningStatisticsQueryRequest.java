package com.sazare.dto;

import com.sazare.common.TranslationDirection;

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
        LocalDate endDate,

        @Pattern(regexp = TranslationDirection.LEARNING_MODE_PATTERN, message = "learningMode 只能是 ZH_TO_JA 或 EN_TO_JA")
        String learningMode
) {
    public LearningStatisticsQueryRequest {
        range = range == null || range.isBlank() ? "LAST_30_DAYS" : range.trim();
        learningMode = learningMode == null || learningMode.isBlank() ? "ZH_TO_JA" : learningMode.trim();
    }

    public LearningStatisticsQueryRequest(String range, LocalDate startDate, LocalDate endDate) {
        this(range, startDate, endDate, "ZH_TO_JA");
    }
}
