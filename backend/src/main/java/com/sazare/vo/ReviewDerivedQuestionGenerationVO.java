package com.sazare.vo;

public record ReviewDerivedQuestionGenerationVO(
        Long questionId,
        Long cycleQuestionId,
        String status
) {
}
