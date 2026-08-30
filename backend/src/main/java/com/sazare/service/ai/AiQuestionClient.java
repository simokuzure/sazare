package com.sazare.service.ai;

import com.sazare.dto.AiArticleGenerationRequest;
import com.sazare.dto.AiQuestionGenerationRequest;
import com.sazare.dto.AiQuestionTagOptionDTO;

import java.util.List;

public interface AiQuestionClient {

    String generateQuestions(
            AiQuestionPrompt prompt,
            AiQuestionGenerationRequest request,
            List<AiQuestionTagOptionDTO> sceneTagOptions,
            List<AiQuestionTagOptionDTO> functionTagOptions
    );

    String generateArticle(
            AiQuestionPrompt prompt,
            AiArticleGenerationRequest request,
            String seed
    );
}
