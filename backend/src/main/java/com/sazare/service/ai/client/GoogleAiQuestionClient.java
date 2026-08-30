package com.sazare.service.ai.client;

import com.sazare.config.AiProperties;
import com.sazare.dto.AiArticleGenerationRequest;
import com.sazare.dto.AiQuestionGenerationRequest;
import com.sazare.dto.AiQuestionTagOptionDTO;
import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import com.sazare.service.ai.AiQuestionClient;
import com.sazare.service.ai.AiQuestionPrompt;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class GoogleAiQuestionClient implements AiQuestionClient {

    private static final String CONTENT_TYPE = "application/json";

    private final AiProperties.Google properties;
    private final ObjectMapper objectMapper;
    private final AiProviderHttpClient httpClient;

    public GoogleAiQuestionClient(
            AiProperties.Google properties,
            ObjectMapper objectMapper,
            AiProviderHttpClient httpClient
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
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
        validateProperties();
        if (article) {
            validateArticleSamplingProperties();
        }
        AiProviderHttpResponse response = httpClient.postJson(
                buildUri(),
                Map.of(
                        "Content-Type", CONTENT_TYPE,
                        "x-goog-api-key", properties.getApiKey().trim()
                ),
                buildRequestBody(prompt, article)
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    GoogleAiErrorMessageBuilder.build(
                            objectMapper,
                            "Google AI 服务返回异常",
                            response.statusCode(),
                            response.body()
                    )
            );
        }
        return extractText(response.body());
    }

    private void validateProperties() {
        if (properties == null
                || properties.getApiKey() == null
                || properties.getApiKey().isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI API key 未配置");
        }
        if (properties.getModel() == null || properties.getModel().isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI model 未配置");
        }
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI baseUrl 未配置");
        }
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

    private URI buildUri() {
        String baseUrl = properties.getBaseUrl().trim().replaceAll("/+$", "");
        String model = normalizeModel(properties.getModel());
        String encodedModel = URLEncoder.encode(model, StandardCharsets.UTF_8);
        return URI.create(baseUrl + "/models/" + encodedModel + ":generateContent");
    }

    private String normalizeModel(String model) {
        String normalizedModel = model.trim();
        if (normalizedModel.startsWith("models/")) {
            return normalizedModel.substring("models/".length());
        }
        return normalizedModel;
    }

    private String buildRequestBody(AiQuestionPrompt prompt, boolean article) {
        Map<String, Object> generationConfig = article
                ? Map.of(
                        "responseMimeType", CONTENT_TYPE,
                        "temperature", properties.getArticleTemperature(),
                        "topP", properties.getArticleTopP()
                )
                : Map.of("responseMimeType", CONTENT_TYPE);
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", prompt.systemPrompt()))
                ),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", prompt.userPrompt()))
                )),
                "generationConfig", generationConfig
        );

        try {
            return objectMapper.writeValueAsString(body);
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI 请求体序列化失败", exception);
        }
    }

    private String extractText(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI 响应为空");
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidates = root.get("candidates");
            if (candidates == null || !candidates.isArray() || candidates.size() == 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI 响应缺少候选结果");
            }

            for (int i = 0; i < candidates.size(); i++) {
                JsonNode content = getChild(candidates.get(i), "content");
                JsonNode parts = getChild(content, "parts");
                if (parts == null || !parts.isArray()) {
                    continue;
                }
                for (int j = 0; j < parts.size(); j++) {
                    JsonNode text = getChild(parts.get(j), "text");
                    if (text != null && text.isTextual() && !text.asString().isBlank()) {
                        return text.asString();
                    }
                }
            }
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI 响应缺少文本内容");
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI 响应解析失败", exception);
        }
    }

    private JsonNode getChild(JsonNode node, String fieldName) {
        if (node == null || !node.isObject()) {
            return null;
        }
        return node.get(fieldName);
    }
}
