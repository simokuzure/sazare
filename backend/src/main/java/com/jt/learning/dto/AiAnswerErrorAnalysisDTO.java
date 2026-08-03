package com.jt.learning.dto;

public record AiAnswerErrorAnalysisDTO(
        String errorTypeCode,
        String original,
        String issue,
        String suggestion,
        String severity,
        String suggestedUserErrorTypeName,
        String suggestedUserErrorTypeDescription
) {
}
