package com.sazare.service.ai.client;

import com.sazare.config.AiProperties;
import com.sazare.service.ai.AiQuestionPrompt;
import com.sazare.service.ai.AiReviewQuestionClient;
import tools.jackson.databind.ObjectMapper;

public class OpenAiReviewQuestionClient implements AiReviewQuestionClient {

    private final OpenAiResponsesClient client;

    public OpenAiReviewQuestionClient(AiProperties.OpenAi properties, ObjectMapper mapper, AiProviderHttpClient http) {
        client = new OpenAiResponsesClient(properties, mapper, http);
    }

    @Override
    public String generateQuestion(AiQuestionPrompt prompt) {
        return client.generate(prompt);
    }
}
