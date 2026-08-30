package com.sazare.service.ai.client;

import com.sazare.config.AiProperties;
import com.sazare.service.ai.AiQuestionPrompt;
import com.sazare.service.ai.AiReviewQuestionClient;
import tools.jackson.databind.ObjectMapper;

public class GoogleAiReviewQuestionClient implements AiReviewQuestionClient {

    private final GoogleAiReviewSupport support;

    public GoogleAiReviewQuestionClient(
            AiProperties.Google properties,
            ObjectMapper objectMapper,
            AiProviderHttpClient httpClient
    ) {
        this.support = new GoogleAiReviewSupport(properties, objectMapper, httpClient);
    }

    @Override
    public String generateQuestion(AiQuestionPrompt prompt) {
        return support.execute(prompt);
    }
}
