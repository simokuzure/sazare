package com.sazare.dto;

public record AiAnswerReviewCommentsDTO(
        String grammarComment,
        String vocabularyComment,
        String naturalnessComment,
        String scenarioComment
) {
}
