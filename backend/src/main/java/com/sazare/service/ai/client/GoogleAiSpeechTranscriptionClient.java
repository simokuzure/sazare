package com.sazare.service.ai.client;

import com.sazare.config.AiProperties;
import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import com.sazare.service.ai.AiSpeechTranscriptionClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class GoogleAiSpeechTranscriptionClient implements AiSpeechTranscriptionClient {
    private static final String JSON_CONTENT_TYPE = "application/json";
    private final AiProperties.Google properties;
    private final ObjectMapper objectMapper;
    private final AiProviderHttpClient httpClient;

    public GoogleAiSpeechTranscriptionClient(
            AiProperties.Google properties, ObjectMapper objectMapper, AiProviderHttpClient httpClient
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public String transcribe(byte[] audio, String mimeType) {
        validateProperties();
        URI uri = URI.create(properties.getBaseUrl().trim().replaceAll("/+$", "") + "/interactions");
        AiProviderHttpResponse response = httpClient.postJson(uri, Map.of(
                "Content-Type", JSON_CONTENT_TYPE,
                "x-goog-api-key", properties.getApiKey().trim()
        ), buildRequestBody(audio, mimeType));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new AiProviderHttpException(response.statusCode(), "Google AI 语音转写服务异常");
        }
        return extractTranscript(response.body());
    }

    private String buildRequestBody(byte[] audio, String mimeType) {
        String model = properties.getTranscriptionModel().trim();
        if (model.startsWith("models/")) model = model.substring("models/".length());
        Map<String, Object> body = Map.of(
                "model", model,
                "store", false,
                "stream", false,
                "input", List.of(Map.of(
                        "type", "audio",
                        "mime_type", mimeType,
                        "data", Base64.getEncoder().encodeToString(audio)
                )),
                "generation_config", Map.of("transcription_config", Map.of(
                        "language_codes", List.of("ja-JP"),
                        "mode", Map.of("type", "verbatim")
                ))
        );
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JacksonException exception) {
            throw failure("Google AI 语音转写请求构建失败");
        }
    }

    private String extractTranscript(String responseBody) {
        try {
            JsonNode root = responseBody == null ? null : objectMapper.readTree(responseBody);
            if (root == null || !root.isObject()) throw invalidResponse();
            if (!"completed".equals(root.path("status").asString())) {
                throw failure("Google AI 语音转写未完成，请重新录音");
            }
            JsonNode steps = root.path("steps");
            if (!steps.isArray()) throw invalidResponse();
            StringBuilder transcript = new StringBuilder();
            for (JsonNode step : steps) {
                if (!step.isObject()) throw invalidResponse();
                if (!"model_output".equals(step.path("type").asString())) continue;
                JsonNode contents = step.path("content");
                if (!contents.isArray()) throw invalidResponse();
                for (JsonNode content : contents) {
                    if (!"text".equals(content.path("type").asString()) || !content.path("text").isTextual()) {
                        throw invalidResponse();
                    }
                    transcript.append(content.get("text").asString());
                }
            }
            // 将供应商的结构化响应转换为内部统一契约，不要求识别模型生成 JSON 文本。
            return objectMapper.writeValueAsString(Map.of("text", transcript.toString()));
        } catch (JacksonException exception) {
            // 解析异常可能包含转写内容，不将异常原因传递给日志。
            throw invalidResponse();
        }
    }

    private void validateProperties() {
        if (properties == null || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw failure("Google AI API key 未配置");
        }
        if (properties.getTranscriptionModel() == null || properties.getTranscriptionModel().isBlank()) {
            throw failure("Google AI 语音转写模型未配置");
        }
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            throw failure("Google AI baseUrl 未配置");
        }
    }

    private BusinessException invalidResponse() {
        return failure("Google AI 语音转写响应格式错误");
    }

    private BusinessException failure(String message) {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, message);
    }
}
