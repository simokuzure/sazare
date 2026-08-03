package com.jt.learning.vo;

import java.time.LocalDateTime;

public record UserAnswerErrorVO(
        Long id,
        Long userAnswerId,
        Long errorTypeId,
        Long userErrorTypeId,
        String originalText,
        String issue,
        String suggestion,
        String severity,
        Integer sortOrder,
        LocalDateTime createdAt
) {
}
