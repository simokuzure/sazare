package com.sazare.service.ai.client;

import com.sazare.config.AiProperties;
import com.sazare.dto.JapaneseCorrectionRequest;
import com.sazare.service.ai.AiJapaneseCorrectionClient;
import com.sazare.service.ai.AiQuestionPrompt;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

public class GoogleAiJapaneseCorrectionClient implements AiJapaneseCorrectionClient {

    private final GoogleGenerateContentClient generateContentClient;

    public GoogleAiJapaneseCorrectionClient(
            AiProperties.Google properties,
            ObjectMapper objectMapper,
            AiProviderHttpClient httpClient
    ) {
        this.generateContentClient = new GoogleGenerateContentClient(properties, objectMapper, httpClient);
    }

    @Override
    public String correct(AiQuestionPrompt prompt, JapaneseCorrectionRequest request) {
        return generateContentClient.generate(
                prompt,
                Map.of("responseMimeType", GoogleGenerateContentClient.JSON_CONTENT_TYPE),
                "日语纠错"
        );
    }
}
