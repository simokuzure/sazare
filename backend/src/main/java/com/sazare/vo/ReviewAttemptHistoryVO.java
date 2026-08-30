package com.sazare.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReviewAttemptHistoryVO(
        Long id,
        Integer cycleNo,
        String questionRole,
        String sourceText,
        String referenceAnswer,
        String answerText,
        String result,
        BigDecimal totalScore,
        Integer quality,
        LocalDateTime createdAt
) {
}
