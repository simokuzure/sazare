package com.sazare.vo;

public record ReviewCycleProgressVO(
        Integer cycleNo,
        Integer successfulReviewCount,
        Integer failedReviewCount,
        Integer netSuccessCount,
        Integer targetSuccessCount,
        Integer originalQuestionCount,
        Integer originalPassedCount,
        Integer retryQuestionCount,
        Integer pendingQuestionCount
) {
}
