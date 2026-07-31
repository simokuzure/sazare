package com.jt.learning.dto;

public record AiAnswerReviewCommentsDTO(
        String grammarComment,
        String vocabularyComment,
        String naturalnessComment,
        String scenarioComment
) {
}
