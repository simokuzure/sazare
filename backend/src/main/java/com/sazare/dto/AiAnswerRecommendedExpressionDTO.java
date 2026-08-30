package com.sazare.dto;

public record AiAnswerRecommendedExpressionDTO(
        String expression,
        String usage,
        String formality,
        String note
) {
}
