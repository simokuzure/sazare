package com.jt.learning.vo;

import java.time.LocalDateTime;

public record UserErrorTypeVO(
        Long id,
        String learningMode,
        Long errorTypeId,
        String errorTypeCode,
        String errorTypeName,
        String name,
        String description,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
