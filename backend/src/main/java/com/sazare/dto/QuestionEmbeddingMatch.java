package com.sazare.dto;

public record QuestionEmbeddingMatch(
        Long questionId,
        String sourceText,
        Double similarity
) {
}
