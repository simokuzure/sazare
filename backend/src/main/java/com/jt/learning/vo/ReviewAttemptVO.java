package com.jt.learning.vo;

import java.time.LocalDateTime;
import java.util.List;

public record ReviewAttemptVO(
        Long userAnswerId,
        Integer quality,
        String result,
        Boolean targetErrorResolved,
        String feedback,
        List<AnswerErrorAnalysisVO> errorAnalysis,
        ReviewCycleProgressVO progress,
        LocalDateTime nextDueAt,
        String cardStatus,
        List<QuestionAnswerVO> standardAnswers,
        String derivedGenerationStatus
) {
}
