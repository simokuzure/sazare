package com.jt.learning.dto;

public record AiArticleGenerationResponseDTO(
        AiArticleBlueprintDTO blueprint,
        AiGeneratedArticleDTO article
) {
}
