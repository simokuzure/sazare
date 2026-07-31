package com.jt.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiAnswerScoringRequest(
        @NotBlank(message = "答案不能为空")
        @Size(max = 2000, message = "答案长度不能超过 2000 个字符")
        String answerText
) {
}
