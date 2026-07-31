package com.jt.learning.dto;

public record AiAnswerErrorAnalysisDTO(
        String type,
        String original,
        String issue,
        String suggestion,
        String severity
) {
}
