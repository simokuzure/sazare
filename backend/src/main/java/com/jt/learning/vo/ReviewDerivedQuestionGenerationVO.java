package com.jt.learning.vo;

public record ReviewDerivedQuestionGenerationVO(
        Long questionId,
        Long cycleQuestionId,
        String status
) {
}
