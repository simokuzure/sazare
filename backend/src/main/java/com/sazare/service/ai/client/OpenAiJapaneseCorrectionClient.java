package com.sazare.service.ai.client;

import com.sazare.config.AiProperties;
import com.sazare.dto.JapaneseCorrectionRequest;
import com.sazare.service.ai.AiJapaneseCorrectionClient;
import com.sazare.service.ai.AiQuestionPrompt;
import tools.jackson.databind.ObjectMapper;

public class OpenAiJapaneseCorrectionClient implements AiJapaneseCorrectionClient {

    private final OpenAiResponsesClient client;

    public OpenAiJapaneseCorrectionClient(AiProperties.OpenAi properties, ObjectMapper mapper, AiProviderHttpClient http) {
        client = new OpenAiResponsesClient(properties, mapper, http);
    }

    @Override
    public String correct(AiQuestionPrompt prompt, JapaneseCorrectionRequest request) {
        return client.generate(prompt);
    }
}
