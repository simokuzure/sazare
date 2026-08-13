package com.jt.learning.service.ai.client;

import com.jt.learning.config.AiProperties;
import com.jt.learning.dto.JapaneseCorrectionRequest;
import com.jt.learning.exception.BusinessException;
import com.jt.learning.exception.ErrorCode;
import com.jt.learning.service.ai.AiJapaneseCorrectionClient;
import com.jt.learning.service.ai.AiQuestionPrompt;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class GoogleAiJapaneseCorrectionClient implements AiJapaneseCorrectionClient {

    private static final String CONTENT_TYPE = "application/json";

    private final AiProperties.Google properties;
    private final ObjectMapper objectMapper;
    private final AiProviderHttpClient httpClient;

    public GoogleAiJapaneseCorrectionClient(
            AiProperties.Google properties,
            ObjectMapper objectMapper,
            AiProviderHttpClient httpClient
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public String correct(AiQuestionPrompt prompt, JapaneseCorrectionRequest request) {
        validateProperties();
        AiProviderHttpResponse response = httpClient.postJson(
                buildUri(),
                Map.of("Content-Type", CONTENT_TYPE, "x-goog-api-key", properties.getApiKey().trim()),
                buildRequestBody(prompt)
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI 日语纠错服务返回异常");
        }
        return extractText(response.body());
    }

    private void validateProperties() {
        if (properties == null || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI API key 未配置");
        }
        if (properties.getModel() == null || properties.getModel().isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI model 未配置");
        }
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI baseUrl 未配置");
        }
    }

    private URI buildUri() {
        String baseUrl = properties.getBaseUrl().trim().replaceAll("/+$", "");
        String model = properties.getModel().trim();
        if (model.startsWith("models/")) {
            model = model.substring("models/".length());
        }
        return URI.create(baseUrl + "/models/"
                + URLEncoder.encode(model, StandardCharsets.UTF_8) + ":generateContent");
    }

    private String buildRequestBody(AiQuestionPrompt prompt) {
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", prompt.systemPrompt()))),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", prompt.userPrompt()))
                )),
                "generationConfig", Map.of("responseMimeType", CONTENT_TYPE)
        );
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI 日语纠错请求体序列化失败");
        }
    }

    private String extractText(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI 日语纠错响应为空");
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidates = root.get("candidates");
            if (candidates == null || !candidates.isArray() || candidates.size() == 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI 日语纠错响应缺少候选结果");
            }
            for (int i = 0; i < candidates.size(); i++) {
                JsonNode content = child(candidates.get(i), "content");
                JsonNode parts = child(content, "parts");
                if (parts == null || !parts.isArray()) {
                    continue;
                }
                for (int j = 0; j < parts.size(); j++) {
                    JsonNode text = child(parts.get(j), "text");
                    if (text != null && text.isTextual() && !text.asString().isBlank()) {
                        return text.asString();
                    }
                }
            }
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI 日语纠错响应缺少文本内容");
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI 日语纠错响应解析失败");
        }
    }

    private JsonNode child(JsonNode node, String fieldName) {
        return node != null && node.isObject() ? node.get(fieldName) : null;
    }
}
