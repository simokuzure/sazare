package com.sazare.dto;

import java.util.Map;

public record AiArticleBlueprintDTO(
        String seed,
        String coreConcept,
        Map<String, String> roles
) {
}
