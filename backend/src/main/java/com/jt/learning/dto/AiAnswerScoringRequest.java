package com.jt.learning.dto;

import jakarta.validation.constraints.NotBlank;

public record AiAnswerScoringRequest(
        @NotBlank(message = "答案不能为空")
        String answerText
) {
}
