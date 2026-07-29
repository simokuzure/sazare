package com.jt.learning.dto;

import java.util.List;

public record AiGeneratedQuestionDTO(
        String questionType,
        String sourceText,
        String contextText,
        String level,
        Integer difficulty,
        String grammarPoint,
        Boolean spoken,
        Boolean business,
        Boolean exam,
        List<String> tagCodes,
        List<AiQuestionAnswerDTO> answers
) {
}
