package com.sazare.service.ai.client;

import com.sazare.config.AiProperties;
import com.sazare.service.ai.AiQuestionPrompt;
import com.sazare.service.ai.AiReviewScoringClient;
import tools.jackson.databind.ObjectMapper;

public class GoogleAiReviewScoringClient implements AiReviewScoringClient {

    private final GoogleAiReviewSupport support;

    public GoogleAiReviewScoringClient(
            AiProperties.Google properties,
            ObjectMapper objectMapper,
            AiProviderHttpClient httpClient
    ) {
        this.support = new GoogleAiReviewSupport(properties, objectMapper, httpClient);
    }

    @Override
    public String scoreAnswer(AiQuestionPrompt prompt) {
        return support.execute(prompt);
    }
}
