package com.jt.learning.vo;

public record QuestionEmbeddingBackfillVO(
        int processedCount,
        long remainingCount
) {
}
