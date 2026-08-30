package com.sazare.dto;

public record AiArticleGenerationResponseDTO(
        AiArticleBlueprintDTO blueprint,
        AiGeneratedArticleDTO article
) {
}
