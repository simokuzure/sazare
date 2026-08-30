package com.sazare.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public record ErrorTypeQueryRequest(
        @Min(value = 1, message = "typeLevel 只能是 1 或 2")
        @Max(value = 2, message = "typeLevel 只能是 1 或 2")
        Integer typeLevel,

        @Positive(message = "parentId 必须大于 0")
        Long parentId,

        Boolean enabled,

        @Min(value = 1, message = "page 必须大于等于 1")
        Integer page,

        @Min(value = 1, message = "size 必须大于等于 1")
        @Max(value = 100, message = "size 不能大于 100")
        Integer size
) {
    public ErrorTypeQueryRequest {
        page = page == null ? 1 : page;
        size = size == null ? 20 : size;
    }
}
