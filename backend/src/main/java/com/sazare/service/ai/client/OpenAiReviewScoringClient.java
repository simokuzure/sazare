package com.sazare.service.ai.client;

import com.sazare.config.AiProperties;
import com.sazare.service.ai.AiQuestionPrompt;
import com.sazare.service.ai.AiReviewScoringClient;
import tools.jackson.databind.ObjectMapper;

public class OpenAiReviewScoringClient implements AiReviewScoringClient {

    private final OpenAiResponsesClient client;

    public OpenAiReviewScoringClient(AiProperties.OpenAi properties, ObjectMapper mapper, AiProviderHttpClient http) {
        client = new OpenAiResponsesClient(properties, mapper, http);
    }

    @Override
    public String scoreAnswer(AiQuestionPrompt prompt) {
        return client.generate(prompt);
    }
}
