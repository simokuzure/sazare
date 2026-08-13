package com.jt.learning.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UserAnswerErrorConfirmItemRequest(
        @NotBlank(message = "mode 不能为空")
        @Pattern(
                regexp = "NEW_USER_ERROR_TYPE|EXISTING_USER_ERROR_TYPE",
                message = "mode 只能是 NEW_USER_ERROR_TYPE 或 EXISTING_USER_ERROR_TYPE"
        )
        String mode,

        @Positive(message = "errorTypeId 必须大于 0")
        Long errorTypeId,

        @Positive(message = "userErrorTypeId 必须大于 0")
        Long userErrorTypeId,

        @Size(max = 128, message = "userErrorTypeName 长度不能超过 128")
        String userErrorTypeName,

        @Size(max = 255, message = "userErrorTypeDescription 长度不能超过 255")
        String userErrorTypeDescription,

        @NotBlank(message = "originalText 不能为空")
        String originalText,

        @NotBlank(message = "issue 不能为空")
        String issue,

        @NotBlank(message = "suggestion 不能为空")
        String suggestion,

        @Size(max = 1000, message = "reviewSourceText 长度不能超过 1000")
        String reviewSourceText,

        @NotBlank(message = "severity 不能为空")
        @Pattern(regexp = "LOW|MEDIUM|HIGH", message = "severity 只能是 LOW、MEDIUM 或 HIGH")
        String severity,

        @NotNull(message = "sortOrder 不能为空")
        @Min(value = 0, message = "sortOrder 不能小于 0")
        @Max(value = 999, message = "sortOrder 不能大于 999")
        Integer sortOrder
) {
    public UserAnswerErrorConfirmItemRequest(
            String mode,
            Long errorTypeId,
            Long userErrorTypeId,
            String userErrorTypeName,
            String userErrorTypeDescription,
            String originalText,
            String issue,
            String suggestion,
            String severity,
            Integer sortOrder
    ) {
        this(mode, errorTypeId, userErrorTypeId, userErrorTypeName, userErrorTypeDescription,
                originalText, issue, suggestion, null, severity, sortOrder);
    }
}
