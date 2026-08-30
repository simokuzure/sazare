package com.sazare.service.ai;

import com.sazare.dto.AiQuestionTagOptionDTO;

import java.util.List;

public interface AiReviewQuestionClient {

    String generateQuestion(AiQuestionPrompt prompt);

    default String generateQuestion(
            AiQuestionPrompt prompt,
            List<AiQuestionTagOptionDTO> sceneTagOptions,
            List<AiQuestionTagOptionDTO> functionTagOptions
    ) {
        return generateQuestion(prompt);
    }

    default String classifyTags(
            AiQuestionPrompt prompt,
            List<AiQuestionTagOptionDTO> sceneTagOptions,
            List<AiQuestionTagOptionDTO> functionTagOptions
    ) {
        return generateQuestion(prompt);
    }
}
