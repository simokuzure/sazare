package com.jt.learning.vo;

public record QuestionAnswerVO(
        Long id,
        String answerText,
        String answerType,
        Boolean primaryAnswer,
        Integer sortOrder
) {
}
