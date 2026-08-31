package com.sazare.service.ai.client;

import com.sazare.config.AiProperties;
import com.sazare.service.ai.AiQuestionPrompt;
import com.sazare.service.ai.AiReviewScoringClient;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

public class GoogleAiReviewScoringClient implements AiReviewScoringClient {

    private final GoogleGenerateContentClient generateContentClient;

    public GoogleAiReviewScoringClient(
            AiProperties.Google properties,
            ObjectMapper objectMapper,
            AiProviderHttpClient httpClient
    ) {
        this.generateContentClient = new GoogleGenerateContentClient(properties, objectMapper, httpClient);
    }

    @Override
    public String scoreAnswer(AiQuestionPrompt prompt) {
        return generateContentClient.generate(
                prompt,
                Map.of("responseMimeType", GoogleGenerateContentClient.JSON_CONTENT_TYPE)
        );
    }
}
