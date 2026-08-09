package com.jt.learning.vo;

import java.time.LocalDateTime;

public record LearningStatisticsWeaknessVO(
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
        String reviewState
) {
}
