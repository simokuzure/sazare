package com.sazare.service.ai.client;

import com.sazare.config.AiProperties;
import com.sazare.dto.AiArticleGenerationRequest;
import com.sazare.dto.AiQuestionGenerationRequest;
import com.sazare.dto.AiQuestionTagOptionDTO;
import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import com.sazare.service.ai.AiQuestionClient;
import com.sazare.service.ai.AiQuestionPrompt;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

public class GoogleAiQuestionClient implements AiQuestionClient {

    private final AiProperties.Google properties;
    private final GoogleGenerateContentClient generateContentClient;

    public GoogleAiQuestionClient(
            AiProperties.Google properties,
            ObjectMapper objectMapper,
        AiProviderHttpClient httpClient
    ) {
        this.properties = properties;
        this.generateContentClient = new GoogleGenerateContentClient(properties, objectMapper, httpClient);
    }

    @Override
    public String generateQuestions(
            AiQuestionPrompt prompt,
            AiQuestionGenerationRequest request,
            List<AiQuestionTagOptionDTO> sceneTagOptions,
            List<AiQuestionTagOptionDTO> functionTagOptions
    ) {
        return generate(prompt, false);
    }

    @Override
    public String generateArticle(AiQuestionPrompt prompt, AiArticleGenerationRequest request, String seed) {
        return generate(prompt, true);
    }

    private String generate(AiQuestionPrompt prompt, boolean article) {
        if (article) {
            validateArticleSamplingProperties();
        }
        return generateContentClient.generate(prompt, generationConfig(article));
    }

    private void validateArticleSamplingProperties() {
        if (!Double.isFinite(properties.getArticleTemperature())
                || properties.getArticleTemperature() < 0.0d
                || properties.getArticleTemperature() > 2.0d) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI 文章 temperature 必须在 0.0 到 2.0 之间");
        }
        if (!Double.isFinite(properties.getArticleTopP())
                || properties.getArticleTopP() <= 0.0d
                || properties.getArticleTopP() > 1.0d) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI 文章 topP 必须大于 0.0 且不超过 1.0");
        }
    }

    private Map<String, Object> generationConfig(boolean article) {
        return article
                ? Map.of(
                        "responseMimeType", GoogleGenerateContentClient.JSON_CONTENT_TYPE,
                        "temperature", properties.getArticleTemperature(),
                        "topP", properties.getArticleTopP()
                )
                : Map.of("responseMimeType", GoogleGenerateContentClient.JSON_CONTENT_TYPE);
    }
}
