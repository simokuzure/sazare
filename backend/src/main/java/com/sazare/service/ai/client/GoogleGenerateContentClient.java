package com.sazare.service.ai.client;

import com.sazare.config.AiProperties;
import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import com.sazare.service.ai.AiQuestionPrompt;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class GoogleGenerateContentClient {

    public static final String JSON_CONTENT_TYPE = "application/json";

    private final AiProperties.Google properties;
    private final ObjectMapper objectMapper;
    private final AiProviderHttpClient httpClient;

    public GoogleGenerateContentClient(
            AiProperties.Google properties,
            ObjectMapper objectMapper,
            AiProviderHttpClient httpClient
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public String generate(AiQuestionPrompt prompt, Map<String, Object> generationConfig) {
        return generate(prompt, generationConfig, "");
    }

    public String generate(
            AiQuestionPrompt prompt,
            Map<String, Object> generationConfig,
            String operationName
    ) {
        validateProperties();
        AiProviderHttpResponse response = httpClient.postJson(
                buildUri(),
                Map.of(
                        "Content-Type", JSON_CONTENT_TYPE,
                        "x-goog-api-key", properties.getApiKey().trim()
                ),
                buildRequestBody(prompt, generationConfig, operationName)
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new AiProviderHttpException(
                    response.statusCode(),
                    GoogleAiErrorMessageBuilder.build(
                            objectMapper,
                            messagePrefix(operationName) + "服务返回异常",
                            response.statusCode(),
                            response.body()
                    )
            );
        }
        return extractText(response.body(), operationName);
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

    private String buildRequestBody(
            AiQuestionPrompt prompt,
            Map<String, Object> generationConfig,
            String operationName
    ) {
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", prompt.systemPrompt()))),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", prompt.userPrompt()))
                )),
                "generationConfig", generationConfig
        );
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JacksonException exception) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    messagePrefix(operationName) + "请求体序列化失败",
                    exception
            );
        }
    }

    private String extractText(String responseBody, String operationName) {
        String prefix = messagePrefix(operationName);
        if (responseBody == null || responseBody.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + "响应为空");
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidates = root.get("candidates");
            if (candidates == null || !candidates.isArray() || candidates.size() == 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + "响应缺少候选结果");
            }
            for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
                JsonNode parts = child(child(candidates.get(candidateIndex), "content"), "parts");
                if (parts == null || !parts.isArray()) {
                    continue;
                }
                for (int partIndex = 0; partIndex < parts.size(); partIndex++) {
                    JsonNode text = child(parts.get(partIndex), "text");
                    if (text != null && text.isTextual() && !text.asString().isBlank()) {
                        return text.asString();
                    }
                }
            }
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + "响应缺少文本内容");
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + "响应解析失败", exception);
        }
    }

    private JsonNode child(JsonNode node, String fieldName) {
        return node != null && node.isObject() ? node.get(fieldName) : null;
    }

    private String messagePrefix(String operationName) {
        if (operationName == null || operationName.isBlank()) {
            return "Google AI ";
        }
        return "Google AI " + operationName.trim();
    }
}
