package com.sazare.service.impl;

import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import com.sazare.service.SpeechTranscriptionService;
import com.sazare.service.ai.AiSpeechTranscriptionClient;
import com.sazare.service.ai.client.AiProviderHttpException;
import com.sazare.vo.SpeechTranscriptionVO;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class SpeechTranscriptionServiceImpl implements SpeechTranscriptionService {
    private final AiSpeechTranscriptionClient client;
    private final ObjectMapper objectMapper;

    public SpeechTranscriptionServiceImpl(AiSpeechTranscriptionClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public SpeechTranscriptionVO transcribe(byte[] audio, String mimeType) {
        String result;
        try {
            result = client.transcribe(audio, mimeType);
        } catch (AiProviderHttpException exception) {
            throw failure(exception.getStatusCode() == 429
                    ? "语音转写请求过于频繁，请稍后重新录音"
                    : "语音转写服务异常，请稍后重新录音");
        } catch (RuntimeException exception) {
            // 供应商错误和解析异常可能含有原始数据，不向全局日志传递原因链。
            throw failure("语音转写请求失败，请检查 Google 配置或网络后重新录音");
        }
        JsonNode root;
        try {
            root = result == null ? null : objectMapper.readTree(result);
        } catch (JacksonException exception) {
            throw failure("语音转写结果格式错误，请重新录音");
        }
        if (root == null || !root.isObject() || root.size() != 1
                || !root.has("text") || !root.get("text").isTextual()) {
            throw failure("语音转写结果格式错误，请重新录音");
        }
        String text = root.get("text").asString().strip();
        if (text.isBlank()) throw failure("未识别到语音，请重新录音");
        return new SpeechTranscriptionVO(text);
    }

    private BusinessException failure(String message) {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, message);
    }
}
