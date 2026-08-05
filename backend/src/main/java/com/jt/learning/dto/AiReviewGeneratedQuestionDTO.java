package com.jt.learning.dto;

import java.util.List;

public record AiReviewGeneratedQuestionDTO(
        String sourceText,
        String contextText,
        String grammarPoint,
        List<AiQuestionAnswerDTO> answers
) {
}
