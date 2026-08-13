package com.jt.learning.dto;

public record AiJapaneseCorrectionErrorDTO(
        String errorTypeCode,
        String original,
        String issue,
        String suggestion,
        String reviewSourceText,
        String severity,
        String suggestedUserErrorTypeName,
        String suggestedUserErrorTypeDescription
) {
    public AiAnswerErrorAnalysisDTO toAnswerError() {
        return new AiAnswerErrorAnalysisDTO(
                errorTypeCode,
                original,
                issue,
                suggestion,
                severity,
                suggestedUserErrorTypeName,
                suggestedUserErrorTypeDescription
        );
    }
}
