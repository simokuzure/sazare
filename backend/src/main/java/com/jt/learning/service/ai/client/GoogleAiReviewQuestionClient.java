package com.jt.learning.service.ai.client;

import com.jt.learning.config.AiProperties;
import com.jt.learning.service.ai.AiQuestionPrompt;
import com.jt.learning.service.ai.AiReviewQuestionClient;
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
