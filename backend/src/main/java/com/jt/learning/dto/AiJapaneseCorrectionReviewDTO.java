package com.jt.learning.dto;

import java.math.BigDecimal;
import java.util.List;

public record AiJapaneseCorrectionReviewDTO(
        AiAnswerScoresDTO scores,
        BigDecimal totalScore,
        String correctedText,
        String overallComment,
        AiJapaneseCorrectionCommentsDTO comments,
        List<AiJapaneseCorrectionErrorDTO> errorAnalysis,
        List<String> revisionSuggestions,
        List<AiAnswerRecommendedExpressionDTO> recommendedExpressions
) {
}
