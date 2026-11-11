package com.sazare.service.ai.client;

import com.sazare.config.AiProperties;
import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import com.sazare.vo.SpeechTranscriptionSessionVO;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GoogleAiLiveSessionClient {
    private static final String SESSION_FAILED = "语音转写会话创建失败，请检查 Google 配置或稍后重试";
    private static final String RATE_LIMITED = "语音转写请求过于频繁，请稍后重新录音";
    private static final String INVALID_RESPONSE = "语音转写会话响应格式错误";
    private final AiProperties.Google properties;
    private final ObjectMapper mapper;
    private final AiProviderHttpClient http;

    public GoogleAiLiveSessionClient(AiProperties.Google properties, ObjectMapper mapper, AiProviderHttpClient http) {
        this.properties = properties;
        this.mapper = mapper;
        this.http = http;
    }

    public SpeechTranscriptionSessionVO createSession() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw failure("Google AI API key 未配置");
        }
        if (properties.getLiveTranscriptionModel() == null || properties.getLiveTranscriptionModel().isBlank()) {
            throw failure("Google AI 实时转写模型未配置");
        }
        try {
            URI configuredBase = URI.create(properties.getBaseUrl().trim());
            URI base = new URI(configuredBase.getScheme(), configuredBase.getRawAuthority(), "/v1alpha", null, null);
            String model = properties.getLiveTranscriptionModel().trim();
            if (!model.startsWith("models/")) model = "models/" + model;
            // 限定令牌只能创建一个日语逐字转写会话，不能用于通用对话或其他模型。
            Map<String, Object> setup = Map.of(
                    "model", model,
                    "generationConfig", Map.of("responseModalities", List.of("TEXT")),
                    "inputAudioTranscription", Map.of("languageCodes", List.of("ja-JP"), "mode", "VERBATIM"),
                    "realtimeInputConfig", Map.of("automaticActivityDetection", Map.of("disabled", true))
            );
            Instant now = Instant.now();
            String body = mapper.writeValueAsString(Map.of(
                    "uses", 1,
                    "expireTime", now.plusSeconds(180).toString(),
                    "newSessionExpireTime", now.plusSeconds(60).toString(),
                    "bidiGenerateContentSetup", setup
            ));
            AiProviderHttpResponse response = http.postJson(URI.create(base + "/auth_tokens"), Map.of(
                    "Content-Type", "application/json", "x-goog-api-key", properties.getApiKey().trim()), body);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw failure(response.statusCode() == 429
                        ? RATE_LIMITED : SESSION_FAILED);
            }
            var root = mapper.readTree(response.body());
            if (root == null || !root.path("name").isTextual() || root.path("name").asString().isBlank()) {
                throw failure(INVALID_RESPONSE);
            }
            String version = base.getPath().substring(base.getPath().lastIndexOf('/') + 1);
            String websocketUrl = "wss://" + base.getRawAuthority()
                    + "/ws/google.ai.generativelanguage." + version
                    + ".GenerativeService.BidiGenerateContentConstrained";
            return new SpeechTranscriptionSessionVO(root.path("name").asString(), websocketUrl, setup, false);
        } catch (BusinessException exception) {
            // HTTP 异常的原因链可能包含认证信息，不传递到全局日志。
            throw failure(Set.of(SESSION_FAILED, RATE_LIMITED, INVALID_RESPONSE).contains(exception.getMessage())
                    ? exception.getMessage() : SESSION_FAILED);
        } catch (Exception exception) {
            throw failure(SESSION_FAILED);
        }
    }

    private BusinessException failure(String message) {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, message);
    }
}
