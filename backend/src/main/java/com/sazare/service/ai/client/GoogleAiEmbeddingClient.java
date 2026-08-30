package com.sazare.service.ai.client;

import com.sazare.config.AiProperties;
import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import com.sazare.service.ai.AiEmbeddingClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GoogleAiEmbeddingClient implements AiEmbeddingClient {

    private static final String CONTENT_TYPE = "application/json";
    private static final int DIMENSION = 768;

    private final AiProperties.Google properties;
    private final ObjectMapper objectMapper;
    private final AiProviderHttpClient httpClient;

    public GoogleAiEmbeddingClient(
            AiProperties.Google properties,
            ObjectMapper objectMapper,
            AiProviderHttpClient httpClient
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public List<Float> embed(String content) {
        validateProperties();
        AiProviderHttpResponse response = httpClient.postJson(
                buildUri(),
                Map.of(
                        "Content-Type", CONTENT_TYPE,
                        "x-goog-api-key", properties.getApiKey().trim()
                ),
                buildRequestBody(content)
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    GoogleAiErrorMessageBuilder.build(
                            objectMapper,
                            "Google AI 嵌入服务返回异常",
                            response.statusCode(),
                            response.body()
                    )
            );
        }
        return extractEmbedding(response.body());
    }

    @Override
    public String modelName() {
        return properties.getEmbeddingModel().trim();
    }

    private void validateProperties() {
        if (properties == null
                || properties.getApiKey() == null
                || properties.getApiKey().isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI API key 未配置");
        }
        if (properties.getEmbeddingModel() == null || properties.getEmbeddingModel().isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI embeddingModel 未配置");
        }
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI baseUrl 未配置");
        }
    }

    private URI buildUri() {
        String baseUrl = properties.getBaseUrl().trim().replaceAll("/+$", "");
        String model = normalizeModel(properties.getEmbeddingModel());
        return URI.create(baseUrl + "/models/" + URLEncoder.encode(model, StandardCharsets.UTF_8) + ":embedContent");
    }

    private String buildRequestBody(String content) {
        Map<String, Object> body = Map.of(
                "model", "models/" + normalizeModel(properties.getEmbeddingModel()),
                "content", Map.of("parts", List.of(Map.of("text", content))),
                "outputDimensionality", DIMENSION,
                "embedContentConfig", Map.of("outputDimensionality", DIMENSION)
        );
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI 嵌入请求体序列化失败", exception);
        }
    }

    private List<Float> extractEmbedding(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI 嵌入响应为空");
        }
        try {
            JsonNode values = objectMapper.readTree(responseBody).path("embedding").path("values");
            if (!values.isArray() || values.size() != DIMENSION) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "Google AI 嵌入维度不正确，期望 " + DIMENSION + "，实际 " + (values.isArray() ? values.size() : 0)
                );
            }
            List<Float> embedding = new ArrayList<>(DIMENSION);
            for (int i = 0; i < values.size(); i++) {
                if (!values.get(i).isNumber()) {
                    throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI 嵌入包含非法数值");
                }
                float value = values.get(i).floatValue();
                if (!Float.isFinite(value)) {
                    throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI 嵌入包含非法数值");
                }
                embedding.add(value);
            }
            return embedding;
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI 嵌入响应解析失败", exception);
        }
    }

    private String normalizeModel(String model) {
        String normalized = model.trim();
        return normalized.startsWith("models/") ? normalized.substring("models/".length()) : normalized;
    }
}
