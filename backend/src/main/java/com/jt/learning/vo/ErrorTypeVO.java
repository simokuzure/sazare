package com.jt.learning.vo;

import java.time.LocalDateTime;

public record ErrorTypeVO(
        Long id,
        Long parentId,
        Integer typeLevel,
        String code,
        String name,
        String description,
        String nameEn,
        String descriptionEn,
        Integer sortOrder,
        Boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
