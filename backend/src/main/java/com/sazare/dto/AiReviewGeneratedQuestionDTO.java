package com.sazare.dto;

import java.util.List;

public record AiReviewGeneratedQuestionDTO(
        String sourceText,
        String contextText,
        String grammarPoint,
        List<String> tagCodes,
        List<AiQuestionAnswerDTO> answers
) {
}
