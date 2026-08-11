package com.jt.learning.service.ai.client;

import com.jt.learning.config.AiProperties;
import com.jt.learning.dto.AiQuestionGenerationRequest;
import com.jt.learning.dto.AiQuestionTagOptionDTO;
import com.jt.learning.exception.BusinessException;
import com.jt.learning.exception.ErrorCode;
import com.jt.learning.service.ai.AiQuestionClient;
import com.jt.learning.service.ai.AiQuestionPrompt;
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
    private static final int ERROR_DETAIL_LIMIT = 500;

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
        validateProperties();
        AiProviderHttpResponse response = httpClient.postJson(
                buildUri(),
                Map.of(
                        "Content-Type", CONTENT_TYPE,
                        "x-goog-api-key", properties.getApiKey().trim()
                ),
                buildRequestBody(prompt)
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    buildErrorMessage(response.statusCode(), response.body())
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

    private String buildRequestBody(AiQuestionPrompt prompt) {
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", prompt.systemPrompt()))
                ),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", prompt.userPrompt()))
                )),
                "generationConfig", Map.of(
                        "responseMimeType", CONTENT_TYPE
                )
        );

        try {
            return objectMapper.writeValueAsString(body);
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI 请求体序列化失败");
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
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI 响应解析失败");
        }
    }

    private String buildErrorMessage(int statusCode, String responseBody) {
        String prefix = "Google AI 服务返回异常: HTTP " + statusCode;
        if (responseBody == null || responseBody.isBlank()) {
            return prefix;
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode error = getChild(root, "error");
            if (error == null) {
                return prefix;
            }
            String errorStatus = textualValue(error, "status");
            String errorDetail = textualValue(error, "message");
            String details = String.join(" - ", List.of(errorStatus, errorDetail).stream()
                    .filter(value -> !value.isBlank())
                    .toList());
            if (details.isBlank()) {
                return prefix;
            }
            return prefix + " " + truncate(details);
        } catch (JacksonException exception) {
            return prefix;
        }
    }

    private String textualValue(JsonNode node, String fieldName) {
        JsonNode value = getChild(node, fieldName);
        if (value == null || !value.isTextual()) {
            return "";
        }
        return value.asString().replaceAll("\\s+", " ").trim();
    }

    private String truncate(String value) {
        if (value.length() <= ERROR_DETAIL_LIMIT) {
            return value;
        }
        return value.substring(0, ERROR_DETAIL_LIMIT);
    }

    private JsonNode getChild(JsonNode node, String fieldName) {
        if (node == null || !node.isObject()) {
            return null;
        }
        return node.get(fieldName);
    }
}
