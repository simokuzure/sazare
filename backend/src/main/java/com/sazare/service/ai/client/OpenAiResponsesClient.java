package com.sazare.service.ai.client;

import com.sazare.config.AiProperties;
import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import com.sazare.service.ai.AiQuestionPrompt;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class OpenAiResponsesClient {

    private static final Pattern ERROR_IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_.\\[\\]]{0,127}");

    private final AiProperties.OpenAi properties;
    private final ObjectMapper objectMapper;
    private final AiProviderHttpClient httpClient;

    public OpenAiResponsesClient(
            AiProperties.OpenAi properties,
            ObjectMapper objectMapper,
            AiProviderHttpClient httpClient
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public String generate(AiQuestionPrompt prompt) {
        validateProperties();
        URI uri = URI.create(properties.getBaseUrl().trim().replaceAll("/+$", "") + "/responses");
        AiProviderHttpResponse response = httpClient.postJson(uri, Map.of(
                "Content-Type", "application/json",
                "Authorization", "Bearer " + properties.getApiKey().trim()
        ), buildRequestBody(prompt));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new AiProviderHttpException(response.statusCode(), errorMessage(response));
        }
        return extractText(response.body());
    }

    private void validateProperties() {
        if (properties == null || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "OpenAI API key 未配置");
        }
        if (properties.getModel() == null || properties.getModel().isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "OpenAI model 未配置");
        }
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "OpenAI baseUrl 未配置");
        }
    }

    private String buildRequestBody(AiQuestionPrompt prompt) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "model", properties.getModel().trim(),
                    // JSON 模式的要求放入输入消息，避免仅使用 instructions 时未通过输入校验。
                    "input", List.of(
                            Map.of("role", "system", "content", prompt.systemPrompt()),
                            Map.of("role", "user", "content", prompt.userPrompt())
                    ),
                    "text", Map.of("format", Map.of("type", "json_object")),
                    "store", false,
                    "stream", false
            ));
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "OpenAI 请求体序列化失败", exception);
        }
    }

    private String extractText(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "OpenAI 响应为空");
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (!"completed".equals(root.path("status").asString())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "OpenAI 未完整完成，请重新尝试");
            }
            JsonNode output = root.path("output");
            if (!output.isArray()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "OpenAI 响应缺少文本内容");
            }
            StringBuilder text = new StringBuilder();
            for (JsonNode item : output) {
                if (!"message".equals(item.path("type").asString())
                        || !"assistant".equals(item.path("role").asString())) {
                    continue;
                }
                if (!"completed".equals(item.path("status").asString())) {
                    throw new BusinessException(ErrorCode.BUSINESS_ERROR, "OpenAI 未完整完成，请重新尝试");
                }
                JsonNode content = item.path("content");
                if (!content.isArray()) {
                    continue;
                }
                for (JsonNode part : content) {
                    String type = part.path("type").asString();
                    if ("refusal".equals(type)) {
                        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "OpenAI 无法处理本次请求");
                    }
                    JsonNode value = part.path("text");
                    if ("output_text".equals(type) && value.isTextual()) {
                        text.append(value.asString());
                    }
                }
            }
            if (text.toString().isBlank()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "OpenAI 响应缺少文本内容");
            }
            String result = text.toString();
            JsonNode json = objectMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(result);
            if (!json.isObject()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "OpenAI 输出必须为 JSON 对象");
            }
            return result;
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "OpenAI 响应格式错误");
        }
    }

    private String errorMessage(AiProviderHttpResponse response) {
        String message = switch (response.statusCode()) {
            case 400 -> "OpenAI 请求参数不合法（HTTP 400）";
            case 401 -> "OpenAI 身份验证失败，请检查 API key";
            case 403 -> "OpenAI 请求被拒绝，请检查账户和模型权限";
            case 429 -> "OpenAI 请求受限，请检查额度或稍后重试";
            default -> "OpenAI 服务请求失败（HTTP " + response.statusCode() + "）";
        };
        if (response.body() == null || response.body().isBlank()) {
            return message;
        }
        try {
            JsonNode error = objectMapper.readTree(response.body()).path("error");
            List<String> details = new ArrayList<>();
            // 供应商 message 可能回显密钥或聊天原文，仅保留有界的错误标识符。
            for (String field : List.of("type", "code", "param")) {
                JsonNode value = error.path(field);
                if (value.isTextual() && ERROR_IDENTIFIER.matcher(value.asString()).matches()
                        && !value.asString().contains(properties.getApiKey().trim())) {
                    details.add(field + "=" + value.asString());
                }
            }
            return details.isEmpty() ? message : message + "；" + String.join("，", details);
        } catch (JacksonException exception) {
            return message;
        }
    }
}
