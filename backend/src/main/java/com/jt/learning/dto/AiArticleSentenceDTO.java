package com.jt.learning.dto;

public record AiArticleSentenceDTO(
        Integer index,
        String chineseText,
        String japaneseReference
) {
}
