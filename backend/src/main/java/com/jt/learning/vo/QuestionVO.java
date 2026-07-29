package com.jt.learning.vo;

import java.time.LocalDateTime;
import java.util.List;

public record QuestionVO(
        Long id,
        String questionType,
        String sourceText,
        String contextText,
        String level,
        Integer difficulty,
        String grammarPoint,
        Boolean spoken,
        Boolean business,
        Boolean exam,
        String sourceType,
        Boolean enabled,
        List<TagVO> tags,
        List<QuestionAnswerVO> answers,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
