package com.jt.learning.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record UserAnswerDetailVO(
        Long id,
        Long questionId,
        String questionType,
        String sourceText,
        String contextText,
        String level,
        Integer difficulty,
        String grammarPoint,
        List<TagVO> tags,
        List<QuestionAnswerVO> answers,
        String answerText,
        String answerStatus,
        AnswerScoresVO scores,
        BigDecimal totalScore,
        String overallComment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
