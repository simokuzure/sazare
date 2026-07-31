package com.jt.learning.dto;

import java.math.BigDecimal;
import java.util.List;

public record AiAnswerReviewDTO(
        AiAnswerScoresDTO scores,
        BigDecimal totalScore,
        String overallComment,
        AiAnswerReviewCommentsDTO comments,
        List<AiAnswerErrorAnalysisDTO> errorAnalysis,
        List<String> revisionSuggestions,
        List<AiAnswerRecommendedExpressionDTO> recommendedExpressions
) {
}
