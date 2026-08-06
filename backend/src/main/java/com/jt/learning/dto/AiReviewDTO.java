package com.jt.learning.dto;

import java.util.List;

public record AiReviewDTO(
        Integer quality,
        Boolean targetErrorResolved,
        String feedback,
        AiAnswerScoresDTO scores,
        List<AiAnswerErrorAnalysisDTO> errorAnalysis
) {
}
