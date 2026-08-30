package com.sazare.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record QuestionEmbeddingBackfillRequest(
        @Min(value = 1, message = "batchSize 必须在 1 到 100 之间")
        @Max(value = 100, message = "batchSize 必须在 1 到 100 之间")
        Integer batchSize
) {
    public QuestionEmbeddingBackfillRequest {
        batchSize = batchSize == null ? 100 : batchSize;
    }
}
