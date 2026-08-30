package com.sazare.vo;

public record AnswerReviewCommentsVO(
        String grammarComment,
        String vocabularyComment,
        String naturalnessComment,
        String scenarioComment
) {
}
