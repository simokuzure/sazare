package com.jt.learning.vo;

public record ReviewCycleProgressVO(
        Integer cycleNo,
        Integer successfulReviewCount,
        Integer targetSuccessCount,
        Integer originalQuestionCount,
        Integer originalPassedCount,
        Integer retryQuestionCount,
        Integer pendingQuestionCount
) {
}
