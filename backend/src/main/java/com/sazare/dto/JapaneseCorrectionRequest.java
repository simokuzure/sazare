package com.sazare.dto;

import com.sazare.common.TranslationDirection;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record JapaneseCorrectionRequest(
        @NotBlank(message = "japaneseText 不能为空")
        @Size(max = 5000, message = "japaneseText 长度不能超过 5000")
        String japaneseText,

        @Pattern(regexp = TranslationDirection.LEARNING_MODE_PATTERN, message = "learningMode 只能是 ZH_TO_JA 或 EN_TO_JA")
        String learningMode
) {
    public JapaneseCorrectionRequest {
        learningMode = learningMode == null || learningMode.isBlank() ? "ZH_TO_JA" : learningMode.trim();
    }

    public JapaneseCorrectionRequest(String japaneseText) {
        this(japaneseText, "ZH_TO_JA");
    }
}
