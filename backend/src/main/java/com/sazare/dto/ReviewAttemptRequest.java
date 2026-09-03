package com.sazare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ReviewAttemptRequest(
        @NotNull(message = "cycleQuestionId 不能为空")
        @Positive(message = "cycleQuestionId 必须大于 0")
        Long cycleQuestionId,

        @NotBlank(message = "answerText 不能为空")
        @Size(max = 2000, message = "answerText 长度不能超过 2000")
        String answerText,

        boolean earlyReview
) {

    public ReviewAttemptRequest(Long cycleQuestionId, String answerText) {
        this(cycleQuestionId, answerText, false);
    }
}
