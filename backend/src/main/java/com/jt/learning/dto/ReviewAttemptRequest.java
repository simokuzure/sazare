package com.jt.learning.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ReviewAttemptRequest(
        @NotNull(message = "cycleQuestionId 不能为空")
        @Positive(message = "cycleQuestionId 必须大于 0")
        Long cycleQuestionId,

        @NotNull(message = "expectedAttemptCount 不能为空")
        @Min(value = 0, message = "expectedAttemptCount 不能小于 0")
        Integer expectedAttemptCount,

        @NotBlank(message = "answerText 不能为空")
        @Size(max = 2000, message = "answerText 长度不能超过 2000")
        String answerText,

        boolean earlyReview
) {

    public ReviewAttemptRequest(Long cycleQuestionId, Integer expectedAttemptCount, String answerText) {
        this(cycleQuestionId, expectedAttemptCount, answerText, false);
    }
}
