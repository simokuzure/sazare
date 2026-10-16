package com.sazare.service.ai.client;

import com.sazare.config.AiProperties;
import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import com.sazare.service.ai.AiQuestionPrompt;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public final class GoogleGenerateContentClient {

    public static final String JSON_CONTENT_TYPE = "application/json";
    private static final int MAX_UNAVAILABLE_RETRIES = 2;
    private static final long INITIAL_RETRY_DELAY_MILLIS = 2_000;
    private static final List<String> FLASH_MODELS = List.of(
            "gemini-3.6-flash", "gemini-3.7-flash", "gemini-3.8-flash"
    );

    private final AiProperties.Google properties;
    private final ObjectMapper objectMapper;
    private final AiProviderHttpClient httpClient;
    private final RetrySleeper retrySleeper;

    public GoogleGenerateContentClient(
            AiProperties.Google properties,
            ObjectMapper objectMapper,
            AiProviderHttpClient httpClient
    ) {
        this(properties, objectMapper, httpClient, Thread::sleep);
    }

    GoogleGenerateContentClient(
            AiProperties.Google properties,
            ObjectMapper objectMapper,
            AiProviderHttpClient httpClient,
            RetrySleeper retrySleeper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.retrySleeper = retrySleeper;
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
        AiProviderHttpResponse response = postWithRetryAndQuotaFallback(
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

    /** 聊天导入必须只发送一次请求，不执行重试或模型回退。 */
    public String generateOnce(AiQuestionPrompt prompt, Map<String, Object> config, String operationName) {
        validateProperties();
        String model = properties.getModel().trim().replaceFirst("^models/", "");
        AiProviderHttpResponse response = httpClient.postJson(buildUri(model), Map.of(
                "Content-Type", JSON_CONTENT_TYPE, "x-goog-api-key", properties.getApiKey().trim()),
                buildRequestBody(prompt, config, operationName));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "聊天分析服务请求失败，请稍后重新分析");
        }
        try {
            JsonNode candidates = objectMapper.readTree(response.body()).get("candidates");
            if (candidates == null || !candidates.isArray() || candidates.size() != 1) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "聊天分析响应缺少唯一候选结果");
            }
            JsonNode candidate = candidates.get(0);
            JsonNode finishReason = candidate.get("finishReason");
            if (finishReason == null || !"STOP".equals(finishReason.asString())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "聊天分析未完整完成，请重新分析");
            }
            JsonNode parts = child(child(candidate, "content"), "parts");
            StringBuilder output = new StringBuilder();
            if (parts != null && parts.isArray()) {
                for (JsonNode part : parts) {
                    JsonNode thought = part.get("thought");
                    JsonNode text = part.get("text");
                    if ((thought == null || !thought.asBoolean()) && text != null && text.isTextual()) {
                        output.append(text.asString());
                    }
                }
            }
            if (output.isEmpty()) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "聊天分析响应缺少文本内容");
            return output.toString();
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "聊天分析响应格式错误");
        }
    }

    private AiProviderHttpResponse postWithRetryAndQuotaFallback(String requestBody) {
        String model = properties.getModel().trim();
        if (model.startsWith("models/")) {
            model = model.substring("models/".length());
        }
        List<String> models = new ArrayList<>();
        models.add(model);
        if (FLASH_MODELS.contains(model)) {
            for (String fallback : FLASH_MODELS) {
                if (!fallback.equals(model)) {
                    models.add(fallback);
                }
            }
        }
        Map<String, String> headers = Map.of(
                "Content-Type", JSON_CONTENT_TYPE,
                "x-goog-api-key", properties.getApiKey().trim()
        );
        var candidates = models.iterator();
        String candidate = candidates.next();
        int unavailableRetries = 0;
        while (true) {
            AiProviderHttpResponse response = httpClient.postJson(buildUri(candidate), headers, requestBody);
            if (response.statusCode() == 503 && unavailableRetries < MAX_UNAVAILABLE_RETRIES) {
                long delayMillis = INITIAL_RETRY_DELAY_MILLIS * (1L << unavailableRetries);
                unavailableRetries++;
                log.warn("Google AI 服务暂时不可用，等待后重试: model={}, retry={}/{}, delayMillis={}",
                        candidate, unavailableRetries, MAX_UNAVAILABLE_RETRIES, delayMillis);
                waitBeforeRetry(delayMillis);
                continue;
            }
            if (response.statusCode() != 429 || !candidates.hasNext()) {
                return response;
            }
            log.warn("Google AI 模型额度受限，尝试下一个 Flash 模型: model={}, status=429", candidate);
            candidate = candidates.next();
        }
    }

    private void waitBeforeRetry(long delayMillis) {
        try {
            retrySleeper.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI 重试等待被中断", exception);
        }
    }

    @FunctionalInterface
    interface RetrySleeper {
        void sleep(long delayMillis) throws InterruptedException;
    }

    private URI buildUri(String model) {
        String baseUrl = properties.getBaseUrl().trim().replaceAll("/+$", "");
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
