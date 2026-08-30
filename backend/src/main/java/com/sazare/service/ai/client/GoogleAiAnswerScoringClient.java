package com.sazare.service.ai.client;

import com.sazare.config.AiProperties;
import com.sazare.dto.AiAnswerScoringRequest;
import com.sazare.dto.AiQuestionTagOptionDTO;
import com.sazare.entity.Question;
import com.sazare.entity.QuestionAnswer;
import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import com.sazare.service.ai.AiAnswerScoringClient;
import com.sazare.service.ai.AiQuestionPrompt;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class GoogleAiAnswerScoringClient implements AiAnswerScoringClient {

    private static final String CONTENT_TYPE = "application/json";

    private final AiProperties.Google properties;
    private final ObjectMapper objectMapper;
    private final AiProviderHttpClient httpClient;

    public GoogleAiAnswerScoringClient(
            AiProperties.Google properties,
            ObjectMapper objectMapper,
            AiProviderHttpClient httpClient
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public String scoreAnswer(
            AiQuestionPrompt prompt,
            AiAnswerScoringRequest request,
            Question question,
            List<QuestionAnswer> standardAnswers,
            List<AiQuestionTagOptionDTO> tagOptions
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
