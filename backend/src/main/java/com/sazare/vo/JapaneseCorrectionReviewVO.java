package com.sazare.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record JapaneseCorrectionReviewVO(
        Long userAnswerId,
        Long questionId,
        String answerText,
        String answerStatus,
        AnswerScoresVO scores,
        BigDecimal totalScore,
        String overallComment,
        String revisedText,
        JapaneseCorrectionCommentsVO comments,
        List<JapaneseCorrectionErrorVO> errorAnalysis,
        List<String> revisionSuggestions,
        List<AnswerRecommendedExpressionVO> recommendedExpressions,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
