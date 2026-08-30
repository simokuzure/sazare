package com.sazare.vo;

public record AnswerErrorAnalysisVO(
        String type,
        Long errorTypeId,
        String errorTypeCode,
        String errorTypeName,
        String original,
        String issue,
        String suggestion,
        String severity,
        String suggestedUserErrorTypeName,
        String suggestedUserErrorTypeDescription
) {
}
