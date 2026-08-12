package com.jt.learning.dto;

public record AiArticleSentenceReviewDTO(
        Integer sourceSegmentIndex,
        String sourceText,
        String referenceText,
        String answerExcerpt,
        String revisedText,
        String comment
) {
}
