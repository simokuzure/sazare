package com.sazare.service.ai.client;

import com.sazare.config.AiProperties;
import com.sazare.dto.AiArticleGenerationRequest;
import com.sazare.dto.AiQuestionGenerationRequest;
import com.sazare.dto.AiQuestionTagOptionDTO;
import com.sazare.service.ai.AiQuestionClient;
import com.sazare.service.ai.AiQuestionPrompt;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

public class OpenAiQuestionClient implements AiQuestionClient {

    private final OpenAiResponsesClient client;

    public OpenAiQuestionClient(AiProperties.OpenAi properties, ObjectMapper mapper, AiProviderHttpClient http) {
        client = new OpenAiResponsesClient(properties, mapper, http);
    }

    @Override
    public String generateQuestions(
            AiQuestionPrompt prompt,
            AiQuestionGenerationRequest request,
            List<AiQuestionTagOptionDTO> sceneTagOptions,
            List<AiQuestionTagOptionDTO> functionTagOptions
    ) {
        return client.generate(prompt);
    }

    @Override
    public String generateArticle(AiQuestionPrompt prompt, AiArticleGenerationRequest request, String seed) {
        return client.generate(prompt);
    }
}
