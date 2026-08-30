package com.sazare.dto;

import java.util.List;

public record AiQuestionGenerationResponseDTO(
        List<AiGeneratedQuestionDTO> questions
) {
}
