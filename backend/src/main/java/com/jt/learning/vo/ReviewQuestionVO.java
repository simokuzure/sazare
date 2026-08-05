package com.jt.learning.vo;

import java.util.List;

public record ReviewQuestionVO(
        Long cycleQuestionId,
        Long questionId,
        String questionRole,
        String sourceText,
        String contextText,
        String level,
        Integer difficulty,
        String grammarPoint,
        Boolean spoken,
        Boolean business,
        Boolean exam,
        List<TagVO> tags,
        Integer attemptCount
) {
}
