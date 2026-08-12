package com.jt.learning.vo;

public record ArticleSentenceReviewVO(
        Integer sourceSegmentIndex,
        String sourceText,
        String referenceText,
        String answerExcerpt,
        String revisedText,
        String comment
) {
}
