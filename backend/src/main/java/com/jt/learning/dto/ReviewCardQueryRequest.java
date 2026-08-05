package com.jt.learning.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record ReviewCardQueryRequest(
        @Pattern(regexp = "ACTIVE|MASTERED", message = "status 只能是 ACTIVE 或 MASTERED")
        String status,

        Boolean dueOnly,

        @Min(value = 1, message = "page 必须大于等于 1")
        Integer page,

        @Min(value = 1, message = "size 必须在 1 到 100 之间")
        @Max(value = 100, message = "size 必须在 1 到 100 之间")
        Integer size
) {
    public ReviewCardQueryRequest {
        status = status == null || status.isBlank() ? "ACTIVE" : status.trim();
        dueOnly = dueOnly == null ? Boolean.FALSE : dueOnly;
        page = page == null ? 1 : page;
        size = size == null ? 20 : size;
    }

    public String getStatus() {
        return status;
    }

    public Boolean getDueOnly() {
        return dueOnly;
    }

    public Integer getPage() {
        return page;
    }

    public Integer getSize() {
        return size;
    }
}
