package com.sazare.vo;

public record TagVO(
        Long id,
        String tagType,
        Long parentId,
        String code,
        String name,
        String description,
        String nameEn,
        String descriptionEn,
        Integer sortOrder
) {
}
