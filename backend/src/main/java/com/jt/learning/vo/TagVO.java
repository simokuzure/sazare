package com.jt.learning.vo;

public record TagVO(
        Long id,
        String tagType,
        Long parentId,
        String code,
        String name,
        String description,
        Integer sortOrder
) {
}
