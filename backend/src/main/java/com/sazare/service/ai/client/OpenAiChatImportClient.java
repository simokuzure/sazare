package com.sazare.service.ai.client;

import com.sazare.config.AiProperties;
import com.sazare.service.ai.AiChatImportClient;
import com.sazare.service.ai.AiQuestionPrompt;
import tools.jackson.databind.ObjectMapper;

public class OpenAiChatImportClient implements AiChatImportClient {

    private final OpenAiResponsesClient client;

    public OpenAiChatImportClient(AiProperties.OpenAi properties, ObjectMapper mapper, AiProviderHttpClient http) {
        client = new OpenAiResponsesClient(properties, mapper, http);
    }

    @Override
    public String analyze(AiQuestionPrompt prompt) {
        return client.generate(prompt);
    }
}
