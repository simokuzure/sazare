package com.jt.learning.dto;

public record AiErrorTypeOptionDTO(
        Long id,
        String code,
        String name,
        String description,
        String nameEn,
        String descriptionEn,
        String parentCode,
        String parentName,
        String parentNameEn
) {
    public AiErrorTypeOptionDTO(Long id, String code, String name, String description,
                                String parentCode, String parentName) {
        this(id, code, name, description, null, null, parentCode, parentName, null);
    }
}
