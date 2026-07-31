package com.jt.learning.vo;

public record AnswerErrorAnalysisVO(
        String type,
        String original,
        String issue,
        String suggestion,
        String severity
) {
}
