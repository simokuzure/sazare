package com.jt.learning.dto;

public record QuestionEmbeddingMatch(
        Long questionId,
        Double similarity
) {
}
