package com.jt.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JapaneseCorrectionRequest(
        @NotBlank(message = "japaneseText 不能为空")
        @Size(max = 5000, message = "japaneseText 长度不能超过 5000")
        String japaneseText
) {
}
