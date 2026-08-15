package com.jt.learning.dto;

public record QuestionEmbeddingMatch(
        Long questionId,
        String sourceText,
        Double similarity
) {
}
