package com.jt.learning.dto;

import java.time.LocalDateTime;

public record LearningStatisticsWeaknessRow(
        Long userErrorTypeId,
        String userErrorTypeName,
        String userErrorTypeStatus,
        Long errorTypeId,
        String errorTypeCode,
        String errorTypeName,
        Long confirmedCount,
        Long lowSeverityCount,
        Long mediumSeverityCount,
        Long highSeverityCount,
        LocalDateTime lastConfirmedAt,
        String reviewCardStatus,
        LocalDateTime reviewCardDueAt
) {
}
