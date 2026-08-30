package com.sazare.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UserAnswerErrorConfirmRequest(
        @NotEmpty(message = "errors 不能为空")
        @Size(max = 20, message = "一次最多确认 20 条错误")
        List<@Valid UserAnswerErrorConfirmItemRequest> errors
) {
}
