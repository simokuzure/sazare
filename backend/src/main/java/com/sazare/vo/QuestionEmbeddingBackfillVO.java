package com.sazare.vo;

public record QuestionEmbeddingBackfillVO(
        int processedCount,
        long remainingCount
) {
}
