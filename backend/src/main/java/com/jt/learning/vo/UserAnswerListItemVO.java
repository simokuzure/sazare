package com.jt.learning.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UserAnswerListItemVO(
        Long id,
        Long questionId,
        String questionType,
        String sourceText,
        String level,
        Integer difficulty,
        String answerText,
        String answerStatus,
        AnswerScoresVO scores,
        BigDecimal totalScore,
        String revisedText,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
