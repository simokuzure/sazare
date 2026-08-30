package com.sazare.dto;

import jakarta.validation.constraints.NotNull;

public record QuestionEnabledRequest(
        @NotNull(message = "enabled 不能为空")
        Boolean enabled
) {
}
