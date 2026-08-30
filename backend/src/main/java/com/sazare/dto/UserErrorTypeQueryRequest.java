package com.sazare.dto;

import com.sazare.common.TranslationDirection;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record UserErrorTypeQueryRequest(
        @Pattern(regexp = "ACTIVE|ARCHIVED", message = "status 只能是 ACTIVE 或 ARCHIVED")
        String status,

        @Min(value = 1, message = "page 必须大于等于 1")
        Integer page,

        @Min(value = 1, message = "size 必须大于等于 1")
        @Max(value = 100, message = "size 不能大于 100")
        Integer size,

        @Pattern(regexp = TranslationDirection.LEARNING_MODE_PATTERN, message = "learningMode 只能是 ZH_TO_JA 或 EN_TO_JA")
        String learningMode
) {
    public UserErrorTypeQueryRequest {
        status = status == null || status.isBlank() ? "ACTIVE" : status.trim();
        page = page == null ? 1 : page;
        size = size == null ? 20 : size;
        learningMode = learningMode == null || learningMode.isBlank() ? "ZH_TO_JA" : learningMode.trim();
    }

    public UserErrorTypeQueryRequest(String status, Integer page, Integer size) {
        this(status, page, size, "ZH_TO_JA");
    }
}
