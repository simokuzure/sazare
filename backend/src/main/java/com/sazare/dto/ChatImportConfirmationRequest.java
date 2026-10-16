package com.sazare.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record ChatImportConfirmationRequest(
        @NotBlank(message = "学习模式不能为空")
        @Pattern(regexp = "ZH_TO_JA|EN_TO_JA", message = "学习模式只能是 ZH_TO_JA 或 EN_TO_JA") String learningMode,
        @NotEmpty(message = "请选择需要确认的候选")
        @Size(max = 200, message = "一次最多确认 200 个候选") List<@NotNull(message = "候选不能为空") @Valid Item> items
) {
    public record Item(
            @NotBlank(message = "确认方式不能为空")
            @Pattern(regexp = "NEW_USER_ERROR_TYPE|EXISTING_USER_ERROR_TYPE", message = "确认方式不合法") String mode,
            @Positive(message = "错误分类 ID 必须大于 0") Long errorTypeId,
            @Positive(message = "用户错误类型 ID 必须大于 0") Long userErrorTypeId,
            @Size(max = 128, message = "复习重点名称不能超过 128 字符") String name,
            @Size(max = 255, message = "复习重点说明不能超过 255 字符") String description,
            @NotBlank(message = "严重程度不能为空")
            @Pattern(regexp = "LOW|MEDIUM|HIGH", message = "严重程度只能是 LOW、MEDIUM 或 HIGH") String severity,
            @NotBlank(message = "复习题面不能为空")
            @Size(max = 1000, message = "复习题面不能超过 1000 字符") String reviewSourceText,
            @NotBlank(message = "日语参考答案不能为空")
            @Size(max = 2000, message = "日语参考答案不能超过 2000 字符") String referenceAnswer
    ) { }
}
