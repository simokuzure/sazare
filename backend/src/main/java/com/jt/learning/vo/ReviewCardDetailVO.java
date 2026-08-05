package com.jt.learning.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReviewCardDetailVO(
        Long id,
        Long userErrorTypeId,
        String userErrorTypeName,
        String userErrorTypeDescription,
        Long errorTypeId,
        String errorTypeCode,
        String errorTypeName,
        String status,
        BigDecimal easeFactor,
        Integer repetitionCount,
        Integer intervalDays,
        Integer lapseCount,
        LocalDateTime dueAt,
        LocalDateTime lastReviewedAt,
        LocalDateTime masteredAt,
        String reviewState,
        ReviewCycleProgressVO progress,
        ReviewQuestionVO currentQuestion
) {
}
