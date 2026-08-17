package com.jt.learning.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewCardCreateRequest(
        @NotBlank(message = "复习重点不能为空")
        @Size(max = 128, message = "复习重点长度不能超过 128")
        String name,

        @NotBlank(message = "目标日语表达不能为空")
        @Size(max = 2000, message = "目标日语表达长度不能超过 2000")
        String targetExpression,

        @Min(value = 0, message = "中文原句索引不能小于 0")
        Integer sourceSegmentIndex,

        @Size(max = 1000, message = "复习题中文长度不能超过 1000")
        String reviewSourceText
) {
}
