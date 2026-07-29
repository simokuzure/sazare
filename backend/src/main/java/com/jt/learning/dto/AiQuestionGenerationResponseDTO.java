package com.jt.learning.dto;

import java.util.List;

public record AiQuestionGenerationResponseDTO(
        List<AiGeneratedQuestionDTO> questions
) {
}
