package com.jt.learning.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ReviewAttemptVO(
        Long userAnswerId,
        Integer quality,
        String result,
        Boolean targetErrorResolved,
        String feedback,
        AnswerScoresVO scores,
        BigDecimal totalScore,
        List<AnswerErrorAnalysisVO> errorAnalysis,
        ReviewCycleProgressVO progress,
        LocalDateTime nextDueAt,
        String cardStatus,
        List<QuestionAnswerVO> standardAnswers,
        String derivedGenerationStatus
) {
}
