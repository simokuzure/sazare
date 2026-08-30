package com.sazare.vo;

public record JapaneseCorrectionErrorVO(
        String type,
        Long errorTypeId,
        String errorTypeCode,
        String errorTypeName,
        String original,
        String issue,
        String suggestion,
        String reviewSourceText,
        String severity,
        String suggestedUserErrorTypeName,
        String suggestedUserErrorTypeDescription
) {
}
