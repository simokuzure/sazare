package com.jt.learning.dto;

public record AiQuestionAnswerDTO(
        String answerText,
        String answerType,
        Boolean primaryAnswer,
        Integer sortOrder
) {
}
