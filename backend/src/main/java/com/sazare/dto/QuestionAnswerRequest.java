package com.sazare.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record QuestionAnswerRequest(
        @NotBlank(message = "answerText 不能为空")
        String answerText,

        @NotBlank(message = "answerType 不能为空")
        @Pattern(regexp = "STANDARD|REFERENCE", message = "answerType 只能是 STANDARD 或 REFERENCE")
        String answerType,

        @NotNull(message = "primaryAnswer 不能为空")
        Boolean primaryAnswer,

        @NotNull(message = "sortOrder 不能为空")
        @Min(value = 0, message = "sortOrder 必须大于等于 0")
        Integer sortOrder
) {
}
