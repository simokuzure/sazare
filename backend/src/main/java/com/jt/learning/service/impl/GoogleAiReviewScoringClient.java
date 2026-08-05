package com.jt.learning.service.impl;

import com.jt.learning.config.AiProperties;
import com.jt.learning.service.AiQuestionPrompt;
import com.jt.learning.service.AiReviewScoringClient;
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
