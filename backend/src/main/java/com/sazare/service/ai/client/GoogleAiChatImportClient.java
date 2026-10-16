package com.sazare.service.ai.client;

import com.sazare.config.AiProperties;
import com.sazare.service.ai.AiChatImportClient;
import com.sazare.service.ai.AiQuestionPrompt;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;

public class GoogleAiChatImportClient implements AiChatImportClient {
    private final GoogleGenerateContentClient client;

    public GoogleAiChatImportClient(AiProperties.Google properties, ObjectMapper mapper, AiProviderHttpClient http) {
        client = new GoogleGenerateContentClient(properties, mapper, http);
    }

    @Override
    public String analyze(AiQuestionPrompt prompt) {
        return client.generateOnce(prompt, Map.of("responseMimeType", "application/json"), "聊天分析");
    }
}
