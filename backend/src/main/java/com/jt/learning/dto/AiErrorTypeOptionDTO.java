package com.jt.learning.dto;

public record AiErrorTypeOptionDTO(
        Long id,
        String code,
        String name,
        String description,
        String parentCode,
        String parentName
) {
}
