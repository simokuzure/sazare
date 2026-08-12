package com.jt.learning.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AnswerReviewVO(
        Long userAnswerId,
        Long questionId,
        String answerText,
        String answerStatus,
        AnswerScoresVO scores,
        BigDecimal totalScore,
        String overallComment,
        AnswerReviewCommentsVO comments,
        String revisedAnswer,
        List<ArticleSentenceReviewVO> sentenceReviews,
        List<AnswerErrorAnalysisVO> errorAnalysis,
        List<String> revisionSuggestions,
        List<AnswerRecommendedExpressionVO> recommendedExpressions,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
